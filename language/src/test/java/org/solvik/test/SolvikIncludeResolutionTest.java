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
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.IncludeResolutionResult;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.source.SourceCatalog;
import org.solvik.source.SourceFile;

/** Include graph expansion, duplicate-file, cycle, and failure coverage. */
public final class SolvikIncludeResolutionTest {

    private static List<String> declarationNames(CompilationUnitNode unit) {
        return unit.declarations().stream().map(d -> ((FunctionDeclNode) d).name()).toList();
    }

    private static IncludeResolutionResult success(String rootName, Map<String, String> files) {
        IncludeResolutionResult result = VirtualIncludeFiles.resolve(rootName, files);
        assertTrue("resolution must succeed: " + result.diagnostics().all(), result.isSuccess());
        return result;
    }

    private static List<Diagnostic> failures(String rootName, Map<String, String> files) {
        IncludeResolutionResult result = VirtualIncludeFiles.resolve(rootName, files);
        assertFalse("resolution must fail", result.isSuccess());
        assertTrue(result.diagnostics().hasErrors());
        assertTrue("failure must expose no unit", result.unit().isEmpty());
        return result.diagnostics().all();
    }

    @Test
    public void depthFirstOrderSplicesAtIncludePosition() {
        IncludeResolutionResult resolved = success("root.sol", Map.of( //
                        "root.sol", "include \"a.sol\"\ninclude \"b.sol\"\nfunc rootFn(): Unit {\n}\n", //
                        "a.sol", "include \"common.sol\"\nfunc aFn(): Unit {\n}\n", //
                        "b.sol", "include \"common.sol\"\nfunc bFn(): Unit {\n}\n", //
                        "common.sol", "func commonFn(): Unit {\n}\n"));
        assertEquals(List.of("commonFn", "aFn", "bFn", "rootFn"), declarationNames(resolved.requireUnit()));
    }

    @Test
    public void repeatedIncludeExpandsOnce() {
        IncludeResolutionResult resolved = success("root.sol", Map.of( //
                        "root.sol", "include \"a.sol\"\ninclude \"a.sol\"\n", //
                        "a.sol", "func aFn(): Unit {\n}\n"));
        assertEquals(List.of("aFn"), declarationNames(resolved.requireUnit()));
    }

    @Test
    public void normalizedPathsResolveToSameIncludeOnce() {
        IncludeResolutionResult resolved = success("root.sol", Map.of( //
                        "root.sol", "include \"lib/x.sol\"\ninclude \"lib/./x.sol\"\n", //
                        "lib/x.sol", "func xFn(): Unit {\n}\n"));
        assertEquals(List.of("xFn"), declarationNames(resolved.requireUnit()));
    }

    @Test
    public void nestedRelativeIncludeResolvesAgainstIncludingFile() {
        IncludeResolutionResult resolved = success("root.sol", Map.of( //
                        "root.sol", "include \"lib/a.sol\"\n", //
                        "lib/a.sol", "include \"b.sol\"\nfunc aFn(): Unit {\n}\n", //
                        "lib/b.sol", "func bFn(): Unit {\n}\n"));
        assertEquals(List.of("bFn", "aFn"), declarationNames(resolved.requireUnit()));
    }

    @Test
    public void absoluteIncludeResolvesDirectly() {
        IncludeResolutionResult resolved = success("root.sol", Map.of( //
                        "root.sol", "include \"/lib/x.sol\"\n", //
                        "lib/x.sol", "func xFn(): Unit {\n}\n"));
        assertEquals(List.of("xFn"), declarationNames(resolved.requireUnit()));
    }

    @Test
    public void selfCycleIsReportedAtTheClosingInclude() {
        List<Diagnostic> diagnostics = failures("root.sol", Map.of("root.sol", "include \"root.sol\"\n"));
        assertEquals(1, diagnostics.size());
        assertEquals(DiagnosticCode.RESOL_INCLUDE_CYCLE, diagnostics.get(0).code());
        assertTrue(diagnostics.get(0).message(), diagnostics.get(0).message().contains("root.sol -> root.sol"));
    }

    @Test
    public void transitiveCycleListsTheChain() {
        List<Diagnostic> diagnostics = failures("root.sol", Map.of( //
                        "root.sol", "include \"a.sol\"\n", //
                        "a.sol", "include \"b.sol\"\n", //
                        "b.sol", "include \"a.sol\"\n"));
        assertEquals(1, diagnostics.size());
        assertEquals(DiagnosticCode.RESOL_INCLUDE_CYCLE, diagnostics.get(0).code());
        String message = diagnostics.get(0).message();
        assertTrue(message, message.contains("a.sol -> b.sol -> a.sol"));
    }

    @Test
    public void missingFileReportsNotFound() {
        List<Diagnostic> diagnostics = failures("root.sol", Map.of("root.sol", "include \"missing.sol\"\n"));
        assertEquals(1, diagnostics.size());
        assertEquals(DiagnosticCode.RESOL_INCLUDE_NOT_FOUND, diagnostics.get(0).code());
    }

    @Test
    public void emptyAndWrongExtensionPathsAreInvalid() {
        assertEquals(DiagnosticCode.RESOL_INCLUDE_INVALID_PATH, failures("root.sol", Map.of("root.sol", "include \"\"\n")).get(0).code());
        assertEquals(DiagnosticCode.RESOL_INCLUDE_INVALID_PATH, failures("root.sol", Map.of("root.sol", "include \"lib.txt\"\n")).get(0).code());
    }

    @Test
    public void invalidEscapeInIncludePathIsLexicalAndSkipsLookup() {
        List<Diagnostic> diagnostics = failures("root.sol", Map.of("root.sol", "include \"lib\\q.sol\"\n"));
        assertEquals(1, diagnostics.size());
        assertEquals(DiagnosticCode.LEXER_INVALID_ESCAPE, diagnostics.get(0).code());
    }

    @Test
    public void independentSiblingFailuresAreAllReported() {
        List<Diagnostic> diagnostics = failures("root.sol", Map.of("root.sol", "include \"missing1.sol\"\ninclude \"missing2.sol\"\n"));
        assertEquals(2, diagnostics.size());
        assertEquals(DiagnosticCode.RESOL_INCLUDE_NOT_FOUND, diagnostics.get(0).code());
        assertEquals(DiagnosticCode.RESOL_INCLUDE_NOT_FOUND, diagnostics.get(1).code());
    }

    @Test
    public void includedParseFailureKeepsItsPhysicalSource() {
        IncludeResolutionResult result = VirtualIncludeFiles.resolve("root.sol", Map.of( //
                        "root.sol", "include \"bad.sol\"\n", //
                        "bad.sol", "func broken(\n"));
        assertFalse(result.isSuccess());
        assertFalse(result.diagnostics().all().isEmpty());
        int sourceId = result.diagnostics().all().get(0).span().sourceId();
        SourceCatalog catalog = result.catalog();
        SourceFile file = catalog.file(sourceId);
        assertEquals("bad.sol", file.name());
    }

    @Test
    public void includingFileWithoutItsOwnIncludeStillRegisters() {
        // root.sol includes a.sol, which is well-formed but declares no include of its own.
        IncludeResolutionResult resolved = success("root.sol", Map.of( //
                        "root.sol", "include \"a.sol\"\n", //
                        "a.sol", "func aFn(): Unit {\n}\n"));
        SourceCatalog catalog = resolved.catalog();
        assertEquals(2, catalog.size());
        assertEquals("root.sol", catalog.file(0).name());
        assertEquals("a.sol", catalog.file(1).name());
    }

    @Test
    public void successfullyResolvedUnitHasNoIncludeNodes() {
        IncludeResolutionResult resolved = success("root.sol", Map.of( //
                        "root.sol", "include \"a.sol\"\n", //
                        "a.sol", "include \"b.sol\"\nfunc aFn(): Unit {\n}\n", //
                        "b.sol", "func bFn(): Unit {\n}\n"));
        assertFalse(resolved.requireUnit().hasUnresolvedIncludes());
        for (var item : resolved.requireUnit().items()) {
            assertFalse(item instanceof org.solvik.ast.declaration.IncludeDeclNode);
        }
    }

    @Test
    public void rootParseContractIsUnchanged() {
        SolvikParseResult parsed = SolvikParser.parse(new SourceFile("plain.sol", "func f(): Unit {\n}\n"));
        assertTrue(parsed.isSuccess());
        assertFalse(parsed.requireAst().hasUnresolvedIncludes());
    }
}
