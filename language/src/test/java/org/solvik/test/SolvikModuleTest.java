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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
import org.junit.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.IncludeDeclNode;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.IncludeResolutionResult;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.source.SourceFile;

/**
 * Module namespaces and aliased includes (docs/LANGUAGE_SPEC.md section 20): parsing, name
 * validation, module merging, module-aware resolution, and end-to-end execution.
 */
public final class SolvikModuleTest {

    private static CompilationUnitNode parseOk(String text) {
        SolvikParseResult result = SolvikParser.parse(new SourceFile("root.sol", text));
        assertTrue("parse must succeed: " + result.diagnostics().all(), result.isSuccess());
        return result.requireAst();
    }

    private static void parseFails(String text) {
        SolvikParseResult result = SolvikParser.parse(new SourceFile("root.sol", text));
        assertFalse("parse must fail: " + text, result.isSuccess());
    }

    private static IncludeResolutionResult resolve(String rootName, Map<String, String> files) {
        return VirtualIncludeFiles.resolve(rootName, files);
    }

    private static SemanticResult analyze(IncludeResolutionResult resolved) {
        assertTrue("resolution must succeed: " + resolved.diagnostics().all(), resolved.isSuccess());
        return SolvikSemanticAnalyzer.analyze(resolved.requireUnit(), resolved.itemScopes());
    }

    private static void assertDiagnostic(SemanticResult result, DiagnosticCode expected) {
        assertFalse("expected failure but got success", result.isSuccess());
        assertTrue("expected " + expected + " but got " + result.diagnostics().all(), //
                        result.diagnostics().all().stream().anyMatch(d -> d.code() == expected));
    }

    private static void assertResolutionDiagnostic(IncludeResolutionResult result, DiagnosticCode expected) {
        assertFalse("expected resolution failure but got success", result.isSuccess());
        assertTrue("expected " + expected + " but got " + result.diagnostics().all(), //
                        result.diagnostics().all().stream().anyMatch(d -> d.code() == expected));
    }

    // ---------------------------------------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------------------------------------

    @Test
    public void moduleAndAliasParse() {
        CompilationUnitNode unit = parseOk("module com_example_util\ninclude \"lib.sol\" alias util\n");
        assertTrue(unit.moduleDeclaration().isPresent());
        assertEquals("com_example_util", unit.moduleDeclaration().get().name());
        IncludeDeclNode include = (IncludeDeclNode) unit.items().get(0);
        assertTrue(include.hasAlias());
        assertEquals("util", include.alias());
    }

    @Test
    public void unaliasedIncludeHasNoAlias() {
        CompilationUnitNode unit = parseOk("include \"lib.sol\"\n");
        IncludeDeclNode include = (IncludeDeclNode) unit.items().get(0);
        assertFalse(include.hasAlias());
        assertNull(include.alias());
    }

    @Test
    public void dottedModuleNameIsRejected() {
        parseFails("module foo.bar\n");
    }

    // ---------------------------------------------------------------------------------------------
    // Name validation, alias binding, merging
    // ---------------------------------------------------------------------------------------------

    @Test
    public void invalidModuleNameIsRejected() {
        IncludeResolutionResult result = resolve("root.sol", Map.of("root.sol", "module Bad\n"));
        assertResolutionDiagnostic(result, DiagnosticCode.RESOL_MODULE_INVALID_NAME);
    }

    @Test
    public void invalidAliasNameIsRejected() {
        IncludeResolutionResult result = resolve("root.sol", Map.of( //
                        "root.sol", "include \"lib.sol\" alias Bad\n", //
                        "lib.sol", "module lib_mod\n"));
        assertResolutionDiagnostic(result, DiagnosticCode.RESOL_MODULE_INVALID_NAME);
    }

    @Test
    public void aliasOnDefaultModuleFileIsRejected() {
        IncludeResolutionResult result = resolve("root.sol", Map.of( //
                        "root.sol", "include \"lib.sol\" alias util\n", //
                        "lib.sol", "func greet(): Unit {\n}\n"));
        assertResolutionDiagnostic(result, DiagnosticCode.RESOL_ALIAS_DEFAULT_MODULE);
    }

    @Test
    public void duplicateAliasIsRejected() {
        IncludeResolutionResult result = resolve("root.sol", Map.of( //
                        "root.sol", "include \"a.sol\" alias util\ninclude \"b.sol\" alias util\n", //
                        "a.sol", "module mod_a\n", //
                        "b.sol", "module mod_b\n"));
        assertResolutionDiagnostic(result, DiagnosticCode.RESOL_ALIAS_DUPLICATE);
    }

    @Test
    public void aliasCollidingWithOwnModuleNameIsRejected() {
        IncludeResolutionResult result = resolve("root.sol", Map.of( //
                        "root.sol", "module app_main\ninclude \"a.sol\" alias app_main\n", //
                        "a.sol", "module mod_a\n"));
        assertResolutionDiagnostic(result, DiagnosticCode.RESOL_ALIAS_DUPLICATE);
    }

    // ---------------------------------------------------------------------------------------------
    // Module-aware resolution
    // ---------------------------------------------------------------------------------------------

    @Test
    public void sameNameInDifferentModulesCoexists() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"a.sol\" alias a\ninclude \"b.sol\" alias b\nval x: a::User = a::User()\nval y: b::User = b::User()\n", //
                        "a.sol", "module mod_a\nclass User {\n}\n", //
                        "b.sol", "module mod_b\nclass User {\n}\n")));
        assertTrue("expected success but got " + result.diagnostics().all(), result.isSuccess());
    }

    @Test
    public void duplicateInSameModuleIsRejected() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"a.sol\"\ninclude \"b.sol\"\n", //
                        "a.sol", "module shared\nclass Dup {\n}\n", //
                        "b.sol", "module shared\nclass Dup {\n}\n")));
        assertDiagnostic(result, DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void defaultModuleDuplicateIsStillRejected() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"a.sol\"\ninclude \"b.sol\"\n", //
                        "a.sol", "func dup(): Unit {\n}\n", //
                        "b.sol", "func dup(): Unit {\n}\n")));
        assertDiagnostic(result, DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void qualifiedFunctionCallResolves() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\" alias m\nval r: Int = m::add(1, 2)\n", //
                        "m.sol", "module math_util\nfunc add(a: Int, b: Int): Int {\n    return a + b\n}\n")));
        assertTrue("expected success but got " + result.diagnostics().all(), result.isSuccess());
    }

    @Test
    public void dotSeparatedReferenceIsNotModuleAccess() {
        // `.` is member access, so `m.add` is a member access on an unknown value `m`, not a call.
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\" alias m\nval r: Int = m.add(1, 2)\n", //
                        "m.sol", "module math_util\nfunc add(a: Int, b: Int): Int {\n    return a + b\n}\n")));
        assertDiagnostic(result, DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void unaliasedModulePrefixResolves() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\"\nval r: Int = math_util::add(1, 2)\n", //
                        "m.sol", "module math_util\nfunc add(a: Int, b: Int): Int {\n    return a + b\n}\n")));
        assertTrue("expected success but got " + result.diagnostics().all(), result.isSuccess());
    }

    @Test
    public void qualifiedConstructionResolves() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\" alias m\nval b: m::Box = m::Box(7)\n", //
                        "m.sol", "module box_mod\nclass Box {\n    val v: Int\n    Box(v: Int) {\n        this.v = v\n    }\n}\n")));
        assertTrue("expected success but got " + result.diagnostics().all(), result.isSuccess());
    }

    @Test
    public void qualifiedEnumVariantResolves() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\" alias m\nval r: m::Result = m::Result.Ok(1)\n", //
                        "m.sol", "module res_mod\nenum Result {\n    Ok(Int)\n    Error(String)\n}\n")));
        assertTrue("expected success but got " + result.diagnostics().all(), result.isSuccess());
    }

    @Test
    public void unknownModulePrefixIsRejected() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\" alias m\nfunc f(x: nope::Thing): Int {\n    return 0\n}\n", //
                        "m.sol", "module mod_x\nclass Thing {\n}\n")));
        assertDiagnostic(result, DiagnosticCode.RESOL_UNKNOWN_MODULE);
    }

    @Test
    public void declarationNamedLikePrefixIsAllowed() {
        // `::` distinguishes a qualified reference from a declaration, so no collision rule is needed.
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\" alias util\nfunc util(): Int {\n    return 1\n}\n", //
                        "m.sol", "module m_mod\n")));
        assertTrue("expected success but got " + result.diagnostics().all(), result.isSuccess());
    }

    @Test
    public void crossModuleExtendsResolves() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"base.sol\" alias base\nclass Derived extends base::Base {\n}\n", //
                        "base.sol", "module base_mod\nopen class Base {\n}\n")));
        assertTrue("expected success but got " + result.diagnostics().all(), result.isSuccess());
    }

    @Test
    public void crossModuleEnumMatchResolves() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\" alias m\nval r: m::Result = m::Result.Ok(5)\nval text = match r {\n    Ok(v) => \"ok\"\n    Error(e) => \"err\"\n}\n", //
                        "m.sol", "module res_mod\nenum Result {\n    Ok(Int)\n    Error(String)\n}\n")));
        assertTrue("expected success but got " + result.diagnostics().all(), result.isSuccess());
    }

    @Test
    public void crossModuleSealedPatternResolves() {
        SemanticResult result = analyze(resolve("root.sol", Map.of( //
                        "root.sol", "include \"m.sol\" alias m\nfunc describe(s: m::Shape): String {\n    return match s {\n        c: m::Circle => \"circle\"\n        _ => \"other\"\n    }\n}\n", //
                        "m.sol", "module shape_mod\nsealed class Shape {\n}\nclass Circle extends Shape {\n}\n")));
        assertTrue("expected success but got " + result.diagnostics().all(), result.isSuccess());
    }

    // ---------------------------------------------------------------------------------------------
    // End-to-end execution
    // ---------------------------------------------------------------------------------------------

    @Test
    public void namespacedProgramRuns() throws IOException {
        Path dir = Files.createTempDirectory("solvik-modules");
        try {
            write(dir, "lib.sol", "module greet_lib\nfunc greet(name: String): String {\n    return \"Hello, \" .. name .. \"!\"\n}\n");
            Path root = write(dir, "root.sol", "module app_main\ninclude \"lib.sol\" alias lib\nprintln(lib::greet(\"Solvik\"))\n");
            assertEquals("Hello, Solvik!\n", evalFile(root));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void sameFunctionNameInDifferentModulesRuns() throws IOException {
        Path dir = Files.createTempDirectory("solvik-modules");
        try {
            write(dir, "a.sol", "module mod_a\nfunc value(): Int {\n    return 1\n}\n");
            write(dir, "b.sol", "module mod_b\nfunc value(): Int {\n    return 2\n}\n");
            Path root = write(dir, "root.sol", "include \"a.sol\" alias a\ninclude \"b.sol\" alias b\nprintln(a::value() + b::value())\n");
            assertEquals("3\n", evalFile(root));
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
        assertNull("unexpected failure: " + failure, failure);
        return out.toString(StandardCharsets.UTF_8);
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
