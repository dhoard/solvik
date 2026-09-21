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

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.IncludeResolutionResult;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/** Static semantics over one include-flattened program. */
public final class SolvikIncludeSemanticTest {

    private static SemanticResult analyze(String rootName, Map<String, String> files) {
        IncludeResolutionResult resolved = VirtualIncludeFiles.resolve(rootName, files);
        assertThat(resolved.isSuccess()).as("resolution must succeed: " + resolved.diagnostics().all()).isTrue();
        return SolvikSemanticAnalyzer.analyze(resolved.requireUnit(), resolved.itemScopes());
    }

    private static void assertOk(SemanticResult result) {
        assertThat(result.isSuccess()).as("expected success but got " + result.diagnostics().all()).isTrue();
    }

    private static void assertCode(SemanticResult result, DiagnosticCode expected) {
        assertThat(result.isSuccess()).as("expected failure").isFalse();
        assertThat(result.diagnostics().all().stream().anyMatch(d -> d.code() == expected)).as("expected " + expected + " but got " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void forwardAndBackwardFunctionReferencesResolveAcrossFiles() {
        assertOk(analyze("root.sol", Map.of( //
                        "root.sol", "include \"lib.sol\"\nfunc use(): Integer {\n    return helper()\n}\n", //
                        "lib.sol", "func helper(): Integer {\n    return 1\n}\n")));
        assertOk(analyze("root.sol", Map.of( //
                        "root.sol", "func helper(): Integer {\n    return 1\n}\ninclude \"lib.sol\"\n", //
                        "lib.sol", "func use(): Integer {\n    return helper()\n}\n")));
    }

    @Test
    public void nominalTypesResolveAcrossFiles() {
        assertOk(analyze("root.sol", Map.of( //
                        "root.sol", "include \"model.sol\"\nfunc use(b: Box): Integer {\n    return 0\n}\n", //
                        "model.sol", "class Box {\n}\n")));
    }

    @Test
    public void genericTypeAndInterfaceSplitAcrossFilesResolve() {
        assertOk(analyze("root.sol", Map.of( //
                        "root.sol", "include \"model.sol\"\nfunc identity<T>(value: T): T {\n    return value\n}\n", //
                        "model.sol", "interface Named {\n    func name(): String\n}\nclass Holder<T> {\n}\n")));
        assertOk(analyze("root.sol", Map.of( //
                        "root.sol", "include \"model.sol\"\nfunc describe(n: Named): String {\n    return n.name()\n}\n", //
                        "model.sol", "interface Named {\n    func name(): String\n}\n")));
    }

    @Test
    public void earlierTopLevelLocalIsVisibleToLaterIncludedStatement() {
        assertOk(analyze("root.sol", Map.of( //
                        "root.sol", "val x: Integer = 1\ninclude \"later.sol\"\n", //
                        "later.sol", "println(x)\n")));
    }

    @Test
    public void laterTopLevelLocalIsUnknownToEarlierIncludedStatement() {
        SemanticResult result = analyze("root.sol", Map.of( //
                        "root.sol", "include \"later.sol\"\nval x: Integer = 1\n", //
                        "later.sol", "println(x)\n"));
        assertThat(result.isSuccess()).as("an earlier statement must not see a later local").isFalse();
    }

    @Test
    public void duplicateFunctionAcrossDistinctFilesIsRejected() {
        assertCode(analyze("root.sol", Map.of( //
                        "root.sol", "include \"a.sol\"\ninclude \"b.sol\"\n", //
                        "a.sol", "func dup(): Unit {\n}\n", //
                        "b.sol", "func dup(): Unit {\n}\n")), DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void duplicateNominalTypeAcrossDistinctFilesIsRejected() {
        assertCode(analyze("root.sol", Map.of( //
                        "root.sol", "include \"a.sol\"\ninclude \"b.sol\"\n", //
                        "a.sol", "class Dup {\n}\n", //
                        "b.sol", "class Dup {\n}\n")), DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void redeclaringBuiltinsIsRejected() {
        assertCode(analyze("root.sol", Map.of("root.sol", "func print(value: Any?): Unit {\n}\n")), DiagnosticCode.RESOL_DUPLICATE_NAME);
        assertCode(analyze("root.sol", Map.of("root.sol", "class String {\n}\n")), DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void explicitMainInAnIncludedFileIsRejected() {
        assertCode(analyze("root.sol", Map.of( //
                        "root.sol", "include \"lib.sol\"\n", //
                        "lib.sol", "func main(): Unit {\n}\n")), DiagnosticCode.SEM_INVALID_ENTRY_POINT);
    }

    @Test
    public void sealedSubclassInSamePhysicalFileIsValid() {
        assertOk(analyze("root.sol", Map.of( //
                        "root.sol", "sealed class Shape {\n}\nclass Circle extends Shape {\n}\n")));
    }

    @Test
    public void sealedSubclassInAnIncludedFileIsRejected() {
        SemanticResult result = analyze("root.sol", Map.of( //
                        "root.sol", "include \"lib.sol\"\nclass Circle extends Shape {\n}\n", //
                        "lib.sol", "sealed class Shape {\n}\n"));
        assertCode(result, DiagnosticCode.SEM_SEALED_SUBTYPE_OUTSIDE_FILE);
        org.solvik.diagnostic.Diagnostic diagnostic = result.diagnostics().all().stream().filter(d -> d.code() == DiagnosticCode.SEM_SEALED_SUBTYPE_OUTSIDE_FILE).findFirst().orElseThrow();
        // The diagnostic points at the subclass in the root physical file (source id 0).
        assertThat(diagnostic.span().sourceId()).isEqualTo(0);
    }

    @Test
    public void declarationOnlyGraphHasNoEntryPoint() {
        IncludeResolutionResult resolved = VirtualIncludeFiles.resolve("root.sol", Map.of( //
                        "root.sol", "include \"lib.sol\"\n", //
                        "lib.sol", "func helper(): Integer {\n    return 1\n}\n"));
        assertThat(resolved.isSuccess()).isTrue();
        SemanticResult result = SolvikSemanticAnalyzer.analyze(resolved.requireUnit());
        assertOk(result);
        assertThat(result.requireProgram().entryPoint().isEmpty()).isTrue();
    }
}
