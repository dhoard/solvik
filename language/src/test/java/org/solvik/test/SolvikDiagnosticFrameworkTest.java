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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
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
        assertThat(span.length()).isEqualTo(4);
        assertThat(span.isEmpty()).isFalse();
        assertThat(span.contains(3)).isTrue();
        assertThat(span.contains(6)).isTrue();
        assertThat(span.contains(7)).as("half-open: end offset is excluded").isFalse();
        assertThat(span.contains(SourceSpan.of(4, 5))).isTrue();
        assertThat(span.contains(SourceSpan.of(4, 8))).isFalse();
        assertThat(span.overlaps(SourceSpan.of(6, 9))).isTrue();
        assertThat(span.overlaps(SourceSpan.of(7, 9))).isFalse();
        assertThat(span.toString()).isEqualTo("[3..7)");
    }

    @Test
    public void spanRejectsInconsistentOffsets() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> SourceSpan.of(-1, 2));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> SourceSpan.of(5, 4));
        SourceSpan empty = SourceSpan.of(2, 2);
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.length()).isEqualTo(0);
    }

    @Test
    public void spanOrdering() {
        List<SourceSpan> spans = List.of(SourceSpan.of(5, 9), SourceSpan.of(1, 2), SourceSpan.of(1, 4));
        assertThat(spans.stream().sorted().toList()).isEqualTo(List.of(SourceSpan.of(1, 2), SourceSpan.of(1, 4), SourceSpan.of(5, 9)));
    }

    @Test
    public void lineColumnDerivationHandlesAllBreakStyles() {
        // '\n', '\r', and '\r\n' each count as one physical line break.
        SourceFile src = new SourceFile("t.sol", "ab\ncd\r\nef\rgh");
        assertThat(src.lineCount()).isEqualTo(4);
        assertThat(src.lineColumnAt(0)).isEqualTo(new LineColumn(1, 1));
        assertThat(src.lineColumnAt(2)).isEqualTo(new LineColumn(1, 3));
        assertThat(src.lineColumnAt(3)).isEqualTo(new LineColumn(2, 1));
        assertThat(src.lineColumnAt(6)).isEqualTo(new LineColumn(2, 4));
        assertThat(src.lineColumnAt(7)).isEqualTo(new LineColumn(3, 1));
        assertThat(src.lineColumnAt(10)).isEqualTo(new LineColumn(4, 1));
    }

    @Test
    public void offsetLookupRoundTripsWithLineColumn() {
        SourceFile src = new SourceFile("r.sol", "line one\nline two\nx");
        for (int offset = 0; offset < src.textLength(); offset++) {
            LineColumn lc = src.lineColumnAt(offset);
            assertThat(src.offsetAt(lc.line(), lc.column())).as("offset " + offset).isEqualTo(offset);
        }
    }

    @Test
    public void sourceSliceAndFormatting() {
        SourceFile src = new SourceFile("x.sol", "func f(): Unit {\n}\n");
        int idx = src.text().indexOf("Unit");
        assertThat(src.slice(SourceSpan.of(idx, idx + 4))).isEqualTo("Unit");
        assertThat(src.formatLocation(SourceSpan.of(0, 3))).isEqualTo("x.sol:1:1");
        int brace = src.text().indexOf('}');
        assertThat(src.formatLocation(SourceSpan.of(brace, brace + 1))).isEqualTo("x.sol:2:1");
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> src.slice(SourceSpan.of(0, src.textLength() + 1)));
    }

    @Test
    public void emptySourceHasOneLine() {
        SourceFile src = new SourceFile("empty.sol", "");
        assertThat(src.lineCount()).isEqualTo(1);
        assertThat(src.lineColumnAt(0)).isEqualTo(new LineColumn(1, 1));
    }

    private static Diagnostic sampleError(String message) {
        return Diagnostic.error(DiagnosticCode.PARSER_UNEXPECTED_TOKEN, SourceSpan.of(1, 4), message);
    }

    @Test
    public void diagnosticCarriesAllFields() {
        Diagnostic d = Diagnostic.expectedFound(DiagnosticCode.PARSER_UNSUPPORTED_LEGACY_SYNTAX, SourceSpan.of(0, 8), "message", "'func'", "'function'");
        assertThat(d.code()).isEqualTo(DiagnosticCode.PARSER_UNSUPPORTED_LEGACY_SYNTAX);
        assertThat(d.code().stableCode()).isEqualTo("SOLV-PARS-004");
        assertThat(d.severity()).isEqualTo(DiagnosticSeverity.ERROR);
        assertThat(d.span()).isEqualTo(SourceSpan.of(0, 8));
        assertThat(d.message()).isEqualTo("message");
        assertThat(d.expected().orElseThrow()).isEqualTo("'func'");
        assertThat(d.found().orElseThrow()).isEqualTo("'function'");
        assertThat(d.isError()).isTrue();
        assertThat(d.toString().contains("SOLV-PARS-004")).isTrue();
        assertThat(d.toString().contains("expected: 'func'")).isTrue();
        assertThat(d.toString().contains("found: 'function'")).isTrue();
    }

    @Test
    public void diagnosticWithoutExpectedFoundOmitsThem() {
        Diagnostic d = sampleError("boom");
        assertThat(d.expected().isEmpty()).isTrue();
        assertThat(d.found().isEmpty()).isTrue();
        assertThat(d.toString().contains("boom")).isTrue();
    }

    @Test
    public void bagTracksErrorsAndPreservesOrder() {
        Diagnostic warn = new Diagnostic(DiagnosticCode.LEXER_ERROR, DiagnosticSeverity.WARNING, SourceSpan.of(0, 1), "warn", null, null);
        Diagnostic err = sampleError("bad");
        DiagnosticBag.Builder b = DiagnosticBag.builder();
        assertThat(b.hasErrors()).isFalse();
        b.add(warn);
        assertThat(b.hasErrors()).as("warnings alone are not errors").isFalse();
        b.add(err);
        assertThat(b.hasErrors()).isTrue();
        DiagnosticBag bag = b.build();
        assertThat(bag.size()).isEqualTo(2);
        assertThat(bag.all()).isEqualTo(List.of(warn, err));
        assertThat(bag.hasErrors()).isTrue();
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> bag.all().add(err));
    }

    @Test
    public void builderCannotBeReusedAfterBuild() {
        DiagnosticBag.Builder b = DiagnosticBag.builder();
        b.add(sampleError("x"));
        b.build();
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> b.add(sampleError("y")));
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(b::build);
    }

    @Test
    public void emptyBagHasNoDiagnostics() {
        DiagnosticBag bag = DiagnosticBag.empty();
        assertThat(bag.isEmpty()).isTrue();
        assertThat(bag.hasErrors()).isFalse();
        assertThat(bag.size()).isEqualTo(0);
        assertThat(bag.all()).isEqualTo(List.of());
    }

    @Test
    public void stableCodesAreUniqueAndLayered() {
        Set<String> seen = new HashSet<>();
        for (DiagnosticCode code : DiagnosticCode.values()) {
            assertThat(seen.add(code.stableCode())).as("duplicate stable code " + code.stableCode()).isTrue();
            String layer = code.stableCode().split("-")[1];
            assertThat(List.of("LEX", "PARS", "RESOL", "TYPE", "SEM", "LOWER").contains(layer)).as("unknown layer " + layer).isTrue();
        }
    }

    @Test
    public void analyzedErrorsAreStableCodedAndSourceLocated() {
        SourceFile file = new SourceFile("prog.sol", "    val x: Integer = \"s\"\n");
        SolvikParseResult parsed = SolvikParser.parse(file);
        assertThat(parsed.isSuccess()).isTrue();
        SemanticResult analyzed = SolvikSemanticAnalyzer.analyze(parsed.requireAst());
        assertThat(analyzed.isSuccess()).isFalse();
        Diagnostic first = analyzed.diagnostics().all().get(0);
        assertThat(first.code().stableCode().startsWith("SOLV-")).isTrue();
        assertThat(first.span().length() > 0).isTrue();
        String location = file.formatLocation(first.span());
        assertThat(location.startsWith("prog.sol:1:")).as("diagnostic must be source-located, was: " + location).isTrue();
        assertThat(first.toString().contains(first.code().stableCode())).as("diagnostic rendering must carry the stable code: " + first).isTrue();
    }

    @Test
    public void spansFromDifferentSourcesNeverCompareContainOrOverlap() {
        SourceSpan first = SourceSpan.of(1, 0, 4);
        SourceSpan second = SourceSpan.of(2, 0, 4);
        assertThat(first.contains(second)).isFalse();
        assertThat(second.contains(first)).isFalse();
        assertThat(first.overlaps(second)).isFalse();
        assertThat(SourceSpan.of(0, 0, 4).overlaps(SourceSpan.of(1, 3, 8))).isFalse();
        assertThat(first.compareTo(second)).isEqualTo(-1);
        assertThat(second.compareTo(first)).isEqualTo(1);
    }

    @Test
    public void sourceCatalogLooksUpByIdAndRejectsDuplicates() {
        SourceFile a = new SourceFile(1, "a.sol", "a");
        SourceFile b = new SourceFile(2, "b.sol", "b");
        SourceCatalog catalog = SourceCatalog.builder().add(a).add(b).build();
        assertThat(catalog.file(1)).isEqualTo(a);
        assertThat(catalog.file(SourceSpan.of(2, 0, 1))).isEqualTo(b);
        assertThat(catalog.size()).isEqualTo(2);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> catalog.file(9));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> SourceCatalog.builder().add(a).add(new SourceFile(1, "c.sol", "c")));
    }

    @Test
    public void sliceAndFormatRejectASpanFromAnotherSource() {
        SourceFile file = new SourceFile(5, "x.sol", "hello");
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> file.slice(SourceSpan.of(0, 0, 2)));
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> file.formatLocation(SourceSpan.of(6, 0, 1)));
        assertThat(file.slice(SourceSpan.of(5, 0, 2))).isEqualTo("he");
    }

    @Test
    public void parserAndAnalyzerSpansCarryTheSourceId() {
        SourceFile file = new SourceFile(7, "seven.sol", "    val x: Integer = \"s\"\n");
        SolvikParseResult parsed = SolvikParser.parse(file);
        assertThat(parsed.isSuccess()).isTrue();
        assertThat(parsed.requireAst().span().sourceId()).isEqualTo(7);
        SemanticResult analyzed = SolvikSemanticAnalyzer.analyze(parsed.requireAst());
        assertThat(analyzed.isSuccess()).isFalse();
        assertThat(analyzed.diagnostics().all().get(0).span().sourceId()).isEqualTo(7);
    }

    @Test
    public void parserErrorSpansCarryTheSourceId() {
        SourceFile file = new SourceFile(7, "seven.sol", "func broken(\n");
        SolvikParseResult parsed = SolvikParser.parse(file);
        assertThat(parsed.isSuccess()).isFalse();
        assertThat(parsed.diagnostics().all().stream().allMatch(d -> d.span().sourceId() == 7)).isTrue();
    }
}
