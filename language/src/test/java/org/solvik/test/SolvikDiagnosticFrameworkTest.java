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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.diagnostic.DiagnosticSeverity;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.source.LineColumn;
import org.solvik.source.SourceCatalog;
import org.solvik.source.SourceFile;
import org.solvik.source.SourceSpan;

/** Unit tests for the Phase 1 diagnostic framework and source/span utilities. */
public final class SolvikDiagnosticFrameworkTest {

    @Test
    public void spanBasics() {
        SourceSpan span = SourceSpan.of(3, 7);
        assertEquals(4, span.length());
        assertFalse(span.isEmpty());
        assertTrue(span.contains(3));
        assertTrue(span.contains(6));
        assertFalse("half-open: end offset is excluded", span.contains(7));
        assertTrue(span.contains(SourceSpan.of(4, 5)));
        assertFalse(span.contains(SourceSpan.of(4, 8)));
        assertTrue(span.overlaps(SourceSpan.of(6, 9)));
        assertFalse(span.overlaps(SourceSpan.of(7, 9)));
        assertEquals("[3..7)", span.toString());
    }

    @Test
    public void spanRejectsInconsistentOffsets() {
        assertThrows(IllegalArgumentException.class, () -> SourceSpan.of(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> SourceSpan.of(5, 4));
        SourceSpan empty = SourceSpan.of(2, 2);
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.length());
    }

    @Test
    public void spanOrdering() {
        List<SourceSpan> spans = List.of(SourceSpan.of(5, 9), SourceSpan.of(1, 2), SourceSpan.of(1, 4));
        assertEquals(List.of(SourceSpan.of(1, 2), SourceSpan.of(1, 4), SourceSpan.of(5, 9)), spans.stream().sorted().toList());
    }

    @Test
    public void lineColumnDerivationHandlesAllBreakStyles() {
        // '\n', '\r', and '\r\n' each count as one physical line break.
        SourceFile src = new SourceFile("t.sol", "ab\ncd\r\nef\rgh");
        assertEquals(4, src.lineCount());
        assertEquals(new LineColumn(1, 1), src.lineColumnAt(0));
        assertEquals(new LineColumn(1, 3), src.lineColumnAt(2));
        assertEquals(new LineColumn(2, 1), src.lineColumnAt(3));
        assertEquals(new LineColumn(2, 4), src.lineColumnAt(6));
        assertEquals(new LineColumn(3, 1), src.lineColumnAt(7));
        assertEquals(new LineColumn(4, 1), src.lineColumnAt(10));
    }

    @Test
    public void offsetLookupRoundTripsWithLineColumn() {
        SourceFile src = new SourceFile("r.sol", "line one\nline two\nx");
        for (int offset = 0; offset < src.textLength(); offset++) {
            LineColumn lc = src.lineColumnAt(offset);
            assertEquals("offset " + offset, offset, src.offsetAt(lc.line(), lc.column()));
        }
    }

    @Test
    public void sourceSliceAndFormatting() {
        SourceFile src = new SourceFile("x.sol", "func f(): Unit {\n}\n");
        int idx = src.text().indexOf("Unit");
        assertEquals("Unit", src.slice(SourceSpan.of(idx, idx + 4)));
        assertEquals("x.sol:1:1", src.formatLocation(SourceSpan.of(0, 3)));
        int brace = src.text().indexOf('}');
        assertEquals("x.sol:2:1", src.formatLocation(SourceSpan.of(brace, brace + 1)));
        assertThrows(IllegalArgumentException.class, () -> src.slice(SourceSpan.of(0, src.textLength() + 1)));
    }

    @Test
    public void emptySourceHasOneLine() {
        SourceFile src = new SourceFile("empty.sol", "");
        assertEquals(1, src.lineCount());
        assertEquals(new LineColumn(1, 1), src.lineColumnAt(0));
    }

    private static Diagnostic sampleError(String message) {
        return Diagnostic.error(DiagnosticCode.PARSER_UNEXPECTED_TOKEN, SourceSpan.of(1, 4), message);
    }

    @Test
    public void diagnosticCarriesAllFields() {
        Diagnostic d = Diagnostic.expectedFound(DiagnosticCode.PARSER_UNSUPPORTED_LEGACY_SYNTAX, SourceSpan.of(0, 8), "message", "'func'", "'function'");
        assertEquals(DiagnosticCode.PARSER_UNSUPPORTED_LEGACY_SYNTAX, d.code());
        assertEquals("SOLV-PARS-004", d.code().stableCode());
        assertEquals(DiagnosticSeverity.ERROR, d.severity());
        assertEquals(SourceSpan.of(0, 8), d.span());
        assertEquals("message", d.message());
        assertEquals("'func'", d.expected().orElseThrow());
        assertEquals("'function'", d.found().orElseThrow());
        assertTrue(d.isError());
        assertTrue(d.toString().contains("SOLV-PARS-004"));
        assertTrue(d.toString().contains("expected: 'func'"));
        assertTrue(d.toString().contains("found: 'function'"));
    }

    @Test
    public void diagnosticWithoutExpectedFoundOmitsThem() {
        Diagnostic d = sampleError("boom");
        assertTrue(d.expected().isEmpty());
        assertTrue(d.found().isEmpty());
        assertTrue(d.toString().contains("boom"));
    }

    @Test
    public void bagTracksErrorsAndPreservesOrder() {
        Diagnostic warn = new Diagnostic(DiagnosticCode.LEXER_ERROR, DiagnosticSeverity.WARNING, SourceSpan.of(0, 1), "warn", null, null);
        Diagnostic err = sampleError("bad");
        DiagnosticBag.Builder b = DiagnosticBag.builder();
        assertFalse(b.hasErrors());
        b.add(warn);
        assertFalse("warnings alone are not errors", b.hasErrors());
        b.add(err);
        assertTrue(b.hasErrors());
        DiagnosticBag bag = b.build();
        assertEquals(2, bag.size());
        assertEquals(List.of(warn, err), bag.all());
        assertTrue(bag.hasErrors());
        assertThrows(UnsupportedOperationException.class, () -> bag.all().add(err));
    }

    @Test
    public void builderCannotBeReusedAfterBuild() {
        DiagnosticBag.Builder b = DiagnosticBag.builder();
        b.add(sampleError("x"));
        b.build();
        assertThrows(IllegalStateException.class, () -> b.add(sampleError("y")));
        assertThrows(IllegalStateException.class, b::build);
    }

    @Test
    public void emptyBagHasNoDiagnostics() {
        DiagnosticBag bag = DiagnosticBag.empty();
        assertTrue(bag.isEmpty());
        assertFalse(bag.hasErrors());
        assertEquals(0, bag.size());
        assertEquals(List.of(), bag.all());
    }

    @Test
    public void stableCodesAreUniqueAndLayered() {
        Set<String> seen = new HashSet<>();
        for (DiagnosticCode code : DiagnosticCode.values()) {
            assertTrue("duplicate stable code " + code.stableCode(), seen.add(code.stableCode()));
            String layer = code.stableCode().split("-")[1];
            assertTrue("unknown layer " + layer, List.of("LEX", "PARS", "RESOL", "TYPE", "SEM", "LOWER").contains(layer));
        }
    }

    @Test
    public void analyzedErrorsAreStableCodedAndSourceLocated() {
        SourceFile file = new SourceFile("prog.sol", "    val x: Int = \"s\"\n");
        SolvikParseResult parsed = SolvikParser.parse(file);
        assertTrue(parsed.isSuccess());
        SemanticResult analyzed = SolvikSemanticAnalyzer.analyze(parsed.requireAst());
        assertFalse(analyzed.isSuccess());
        Diagnostic first = analyzed.diagnostics().all().get(0);
        assertTrue(first.code().stableCode().startsWith("SOLV-"));
        assertTrue(first.span().length() > 0);
        String location = file.formatLocation(first.span());
        assertTrue("diagnostic must be source-located, was: " + location, location.startsWith("prog.sol:1:"));
        assertTrue("diagnostic rendering must carry the stable code: " + first, first.toString().contains(first.code().stableCode()));
    }

    @Test
    public void spansFromDifferentSourcesNeverCompareContainOrOverlap() {
        SourceSpan first = SourceSpan.of(1, 0, 4);
        SourceSpan second = SourceSpan.of(2, 0, 4);
        assertFalse(first.contains(second));
        assertFalse(second.contains(first));
        assertFalse(first.overlaps(second));
        assertFalse(SourceSpan.of(0, 0, 4).overlaps(SourceSpan.of(1, 3, 8)));
        assertEquals(-1, first.compareTo(second));
        assertEquals(1, second.compareTo(first));
    }

    @Test
    public void sourceCatalogLooksUpByIdAndRejectsDuplicates() {
        SourceFile a = new SourceFile(1, "a.sol", "a");
        SourceFile b = new SourceFile(2, "b.sol", "b");
        SourceCatalog catalog = SourceCatalog.builder().add(a).add(b).build();
        assertEquals(a, catalog.file(1));
        assertEquals(b, catalog.file(SourceSpan.of(2, 0, 1)));
        assertEquals(2, catalog.size());
        assertThrows(IllegalArgumentException.class, () -> catalog.file(9));
        assertThrows(IllegalArgumentException.class, () -> SourceCatalog.builder().add(a).add(new SourceFile(1, "c.sol", "c")));
    }

    @Test
    public void sliceAndFormatRejectASpanFromAnotherSource() {
        SourceFile file = new SourceFile(5, "x.sol", "hello");
        assertThrows(IllegalArgumentException.class, () -> file.slice(SourceSpan.of(0, 0, 2)));
        assertThrows(IllegalArgumentException.class, () -> file.formatLocation(SourceSpan.of(6, 0, 1)));
        assertEquals("he", file.slice(SourceSpan.of(5, 0, 2)));
    }

    @Test
    public void parserAndAnalyzerSpansCarryTheSourceId() {
        SourceFile file = new SourceFile(7, "seven.sol", "    val x: Int = \"s\"\n");
        SolvikParseResult parsed = SolvikParser.parse(file);
        assertTrue(parsed.isSuccess());
        assertEquals(7, parsed.requireAst().span().sourceId());
        SemanticResult analyzed = SolvikSemanticAnalyzer.analyze(parsed.requireAst());
        assertFalse(analyzed.isSuccess());
        assertEquals(7, analyzed.diagnostics().all().get(0).span().sourceId());
    }

    @Test
    public void parserErrorSpansCarryTheSourceId() {
        SourceFile file = new SourceFile(7, "seven.sol", "func broken(\n");
        SolvikParseResult parsed = SolvikParser.parse(file);
        assertFalse(parsed.isSuccess());
        assertTrue(parsed.diagnostics().all().stream().allMatch(d -> d.span().sourceId() == 7));
    }
}
