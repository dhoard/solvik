/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.solvik.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.IncludeResolutionResult;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.source.SourceFile;

/**
 * Module namespaces (docs/LANGUAGE_SPEC.md section 20): module-block parsing, name validation,
 * module merging, module-aware resolution, and end-to-end execution.
 */
public final class SolvikModuleTest {

    private static CompilationUnitNode parseOk(String text) {
        SolvikParseResult result = SolvikParser.parse(new SourceFile("root.sol", text));
        assertThat(result.isSuccess()).as("parse must succeed: " + result.diagnostics().all()).isTrue();
        return result.requireAst();
    }

    private static void parseFails(String text) {
        SolvikParseResult result = SolvikParser.parse(new SourceFile("root.sol", text));
        assertThat(result.isSuccess()).as("parse must fail: " + text).isFalse();
    }

    private static IncludeResolutionResult resolve(String rootName, Map<String, String> files) {
        return VirtualIncludeFiles.resolve(rootName, files);
    }

    private static SemanticResult analyze(IncludeResolutionResult resolved) {
        assertThat(resolved.isSuccess()).as("resolution must succeed: " + resolved.diagnostics().all()).isTrue();
        return SolvikSemanticAnalyzer.analyze(resolved.requireUnit());
    }

    private static void assertDiagnostic(SemanticResult result, DiagnosticCode expected) {
        assertThat(result.isSuccess()).as("expected failure but got success").isFalse();
        assertThat(//
                        result.diagnostics().all().stream().anyMatch(d -> d.code() == expected)).as("expected " + expected + " but got " + result.diagnostics().all()).isTrue();
    }

    private static void assertResolutionDiagnostic(IncludeResolutionResult result, DiagnosticCode expected) {
        assertThat(result.isSuccess()).as("expected resolution failure but got success").isFalse();
        assertThat(//
                        result.diagnostics().all().stream().anyMatch(d -> d.code() == expected)).as("expected " + expected + " but got " + result.diagnostics().all()).isTrue();
    }

    // ---------------------------------------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------------------------------------

    @Test
    public void moduleBlockParsesAndCarriesItsDeclarations() {
        CompilationUnitNode unit = parseOk("module util {\n    func add(a: Integer, b: Integer): Integer {\n        return a + b\n    }\n}\n");
        assertThat(unit.modules()).hasSize(1);
        assertThat(unit.modules().get(0).name()).isEqualTo("util");
        // A module block is structural: its declarations remain declarations of the compilation unit,
        // and each one can be asked which module it belongs to.
        assertThat(unit.declarations()).hasSize(1);
        assertThat(unit.moduleNameOf(unit.declarations().get(0))).contains("util");
    }

    @Test
    public void declarationsOutsideEveryBlockBelongToTheDefaultModule() {
        CompilationUnitNode unit = parseOk("func f(): Integer {\n    return 1\n}\n");
        assertThat(unit.modules()).isEmpty();
        assertThat(unit.declarations()).hasSize(1);
        assertThat(unit.moduleNameOf(unit.declarations().get(0))).isEmpty();
    }

    @Test
    public void oneFileMayDeclareModulesAndDefaultModuleStatements() {
        CompilationUnitNode unit = parseOk("module util {\n    func f(): Integer {\n        return 1\n    }\n}\n\nprintln(1)\n");
        assertThat(unit.modules()).hasSize(1);
        assertThat(unit.statements()).hasSize(1);
    }

    @Test
    public void oneFileMayDeclareSeveralModules() {
        CompilationUnitNode unit = parseOk("module first {\n}\nmodule second {\n}\n");
        assertThat(unit.modules()).extracting(module -> module.name()).containsExactly("first", "second");
    }

    @Test
    public void nestedModuleBlockIsRejected() {
        parseFails("module outer {\n    module inner {\n    }\n}\n");
    }

    @Test
    public void statementInsideANamedModuleIsRejected() {
        parseFails("module util {\n    println(1)\n}\n");
    }

    @Test
    public void dottedModuleNameIsRejected() {
        parseFails("module foo.bar {\n}\n");
    }

    @Test
    public void includeAliasModifierIsRejected() {
        // Aliases are gone: a file references another module through the module's own name, so the
        // retired `alias` clause is rejected rather than reinterpreted.
        parseFails("include \"lib.sol\" alias util\n");
    }

    // ---------------------------------------------------------------------------------------------
    // Name validation and module merging
    // ---------------------------------------------------------------------------------------------

    @Test
    public void invalidModuleNameIsRejected() {
        IncludeResolutionResult result = resolve("root.sol", Map.of("root.sol", "module Bad {\n}\n"));
        assertResolutionDiagnostic(result, DiagnosticCode.RESOL_MODULE_INVALID_NAME);
    }

    @Test
    public void invalidModuleNameInAnIncludeFreeRootIsRejected() {
        // A root file with no `include` still declares a module, and section 20 requires its name to
        // satisfy the module naming rule. The name is validated during include resolution, so the
        // include-free root must still reach that validation rather than skipping it.
        String message = evalFailure("module Bad_Name {\n}\n\nprintln(1)\n");
        assertThat(message).as("expected SOLV-RESOL-012 but got: " + message).contains("SOLV-RESOL-012");
    }

    @Test
    public void validModuleNameInAnIncludeFreeRootStillCompilesAndRuns() {
        // The negative half of the same rule: a root module name that satisfies the naming rule must
        // keep compiling and executing, so the validation cannot reject every root declaration.
        assertThat(evalMemory("module app_main {\n}\n\nprintln(1)\n")).isEqualTo("1\n");
    }

    @Test
    public void includeFreeRootReferencesItsOwnModule() {
        // A file may always reference its own module through its declared name, whether or not any
        // include made that name visible.
        assertThat(evalMemory(
                        "module app_main {\n    func value(): Integer {\n        return 7\n    }\n}\n\nprintln(app_main::value())\n"))
                .isEqualTo("7\n");
    }

    @Test
    public void includeFreeRootRejectsAnUnknownModulePrefix() {
        // The negative half: resolving a file's own module name must not make every prefix visible.
        String message = evalFailure(
                "module app_main {\n    func value(): Integer {\n        return 7\n    }\n}\n\nprintln(other_mod::value())\n");
        assertThat(message).as("expected SOLV-RESOL-015 but got: " + message).contains("SOLV-RESOL-015");
    }

    /**
     * Two files that declare the same module merge into one namespace; there is no include-topology
     * rule to violate, because a module name denotes a namespace rather than a prefix bound per file.
     */
    @Test
    public void filesDeclaringTheSameModuleMerge() {
        SemanticResult merged = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"a.sol\"\ninclude \"b.sol\"\n", //
                        "a.sol", "module app {\n    func fa(): Integer {\n        return 1\n    }\n}\n", //
                        "b.sol", "module app {\n    func fb(): Integer {\n        return 2\n    }\n}\n")));
        assertThat(merged.isSuccess()).as("same-module includes merge: " + merged.diagnostics().all()).isTrue();
    }

    @Test
    public void duplicateInSameModuleIsRejected() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"a.sol\"\ninclude \"b.sol\"\n", //
                        "a.sol", "module shared {\n    class Dup {\n    }\n}\n", //
                        "b.sol", "module shared {\n    class Dup {\n    }\n}\n")));
        assertDiagnostic(result, DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void defaultModuleDuplicateIsStillRejected() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"a.sol\"\ninclude \"b.sol\"\n", //
                        "a.sol", "func dup() {\n}\n", //
                        "b.sol", "func dup() {\n}\n")));
        assertDiagnostic(result, DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    // ---------------------------------------------------------------------------------------------
    // Module-aware resolution
    // ---------------------------------------------------------------------------------------------

    @Test
    public void sameNameInDifferentModulesCoexists() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"a.sol\"\ninclude \"b.sol\"\nvar x: mod_a::User = mod_a::User()\nvar y: mod_b::User = mod_b::User()\n", //
                        "a.sol", "module mod_a {\n    class User {\n    }\n}\n", //
                        "b.sol", "module mod_b {\n    class User {\n    }\n}\n")));
        assertThat(result.isSuccess()).as("expected success but got " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void qualifiedFunctionCallResolves() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\"\nvar r: Integer = math_util::add(1, 2)\n", //
                        "m.sol", "module math_util {\n    func add(a: Integer, b: Integer): Integer {\n        return a + b\n    }\n}\n")));
        assertThat(result.isSuccess()).as("expected success but got " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void dotSeparatedReferenceIsNotModuleAccess() {
        // `.` is member access, so `math_util.add` is a member access on an unknown value, not a call.
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\"\nvar r: Integer = math_util.add(1, 2)\n", //
                        "m.sol", "module math_util {\n    func add(a: Integer, b: Integer): Integer {\n        return a + b\n    }\n}\n")));
        assertDiagnostic(result, DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void qualifiedConstructionResolves() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\"\nvar b: box_mod::Box = box_mod::Box(7)\n", //
                        "m.sol", "module box_mod {\n    class Box {\n        var v: Integer\n        Box(v: Integer) {\n            this.v = v\n        }\n    }\n}\n")));
        assertThat(result.isSuccess()).as("expected success but got " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void qualifiedEnumVariantResolves() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\"\nvar r: res_mod::Result = res_mod::Result.Ok(1)\n", //
                        "m.sol", "module res_mod {\n    enum Result {\n        Ok(Integer)\n        Error(String)\n    }\n}\n")));
        assertThat(result.isSuccess()).as("expected success but got " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void unknownModulePrefixIsRejected() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\"\nfunc f(x: nope::Thing): Integer {\n    return 0\n}\n", //
                        "m.sol", "module mod_x {\n    class Thing {\n    }\n}\n")));
        assertDiagnostic(result, DiagnosticCode.RESOL_UNKNOWN_MODULE);
    }

    @Test
    public void declarationNamedLikePrefixIsAllowed() {
        // `::` distinguishes a qualified reference from a declaration, so no collision rule is needed.
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\"\nfunc util(): Integer {\n    return 1\n}\n", //
                        "m.sol", "module m_mod {\n}\n")));
        assertThat(result.isSuccess()).as("expected success but got " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void crossModuleExtendsResolves() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"base.sol\"\nclass Derived extends base_mod::Base {\n}\n", //
                        "base.sol", "module base_mod {\n    class mutable Base {\n    }\n}\n")));
        assertThat(result.isSuccess()).as("expected success but got " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void crossModuleEnumMatchResolves() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\"\nvar r: res_mod::Result = res_mod::Result.Ok(5)\nvar text: String = match r {\n    Ok(v) => \"ok\"\n    Error(e) => \"err\"\n}\n", //
                        "m.sol", "module res_mod {\n    enum Result {\n        Ok(Integer)\n        Error(String)\n    }\n}\n")));
        assertThat(result.isSuccess()).as("expected success but got " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void crossModuleSealedPatternResolves() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\"\nfunc describe(s: shape_mod::Shape): String {\n    return match s {\n        c: shape_mod::Circle => \"circle\"\n        _ => \"other\"\n    }\n}\n", //
                        "m.sol", "module shape_mod {\n    class abstract Shape {\n    }\n    class Circle extends Shape {\n    }\n}\n")));
        assertThat(result.isSuccess()).as("expected success but got " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void userDeclaredObjectIsAllowedInANamedModule() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"o.sol\"\nvar value: obj_mod::Object = obj_mod::Object()\n", //
                        "o.sol", "module obj_mod {\n    class Object {\n    }\n}\n")));
        assertThat(result.isSuccess()).as("expected success but got " + result.diagnostics().all()).isTrue();
    }

    // ---------------------------------------------------------------------------------------------
    // End-to-end execution
    // ---------------------------------------------------------------------------------------------

    @Test
    public void namespacedProgramRuns() throws IOException {
        Path dir = Files.createTempDirectory("solvik-modules");
        try {
            write(dir, "lib.sol", "module greet_lib {\n    func greet(name: String): String {\n        return \"Hello, \" .. name .. \"!\"\n    }\n}\n");
            Path root = write(dir, "root.sol", "module app_main {\n}\n\ninclude \"lib.sol\"\n\nprintln(greet_lib::greet(\"Solvik\"))\n");
            assertThat(evalFile(root)).isEqualTo("Hello, Solvik!\n");
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void sameFunctionNameInDifferentModulesRuns() throws IOException {
        Path dir = Files.createTempDirectory("solvik-modules");
        try {
            write(dir, "a.sol", "module mod_a {\n    func value(): Integer {\n        return 1\n    }\n}\n");
            write(dir, "b.sol", "module mod_b {\n    func value(): Integer {\n        return 2\n    }\n}\n");
            Path root = write(dir, "root.sol", "include \"a.sol\"\ninclude \"b.sol\"\nprintln(mod_a::value() + mod_b::value())\n");
            assertThat(evalFile(root)).isEqualTo("3\n");
        } finally {
            deleteRecursively(dir);
        }
    }

    private static String evalFile(Path root) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotException failure = null;
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(Source.newBuilder("solvik", root.toFile()).build());
        } catch (PolyglotException e) {
            failure = e;
        }
        assertThat(failure).as("unexpected failure: " + failure).isNull();
        return out.toString(StandardCharsets.UTF_8);
    }

    /** Evaluates an in-memory program that is expected to fail and returns the failure message. */
    private static String evalFailure(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(memorySource(text));
        } catch (PolyglotException e) {
            return e.getMessage();
        }
        throw new AssertionError("expected a compile failure but the program ran: " + text);
    }

    /** Evaluates an in-memory program that is expected to succeed and returns its output. */
    private static String evalMemory(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(memorySource(text));
        } catch (PolyglotException e) {
            throw new AssertionError("unexpected failure: " + e.getMessage(), e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Source memorySource(String text) {
        try {
            return Source.newBuilder("solvik", text, "root.sol").build();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static Path write(Path directory, String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }
}
