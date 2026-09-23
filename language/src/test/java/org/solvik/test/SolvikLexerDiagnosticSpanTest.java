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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.source.SourceFile;
import org.solvik.source.SourceSpan;

/**
 * Span accuracy for lexical diagnostics under every physical line-terminator style.
 *
 * <p>{@code docs/LANGUAGE_SPEC.md} section 16 treats {@code \n}, {@code \r}, and {@code \r\n} as
 * physical newlines, and {@link SourceFile} implements exactly that model. ANTLR's lexer, however,
 * advances its own line counter only on {@code \n}. A diagnostic positioned through ANTLR's
 * reported line and column therefore lands on the wrong character in a lone-carriage-return source.
 * These tests pin the reported span to the offending character itself, which is what
 * {@code AGENTS.md} requires of a "precise and source-located" diagnostic, and pin that the
 * newline-delimited behaviour every other suite depends on is unchanged.
 *
 * <p>Each case asserts three ways: the span offsets, the exact source slice the span selects, and
 * the derived {@code name:line:column} display position.
 */
public final class SolvikLexerDiagnosticSpanTest {

    private static final String BAD_CHARACTER = "@";

    private static SourceFile source(String text) {
        return new SourceFile("spans.sol", text);
    }

    /** Every lexical diagnostic of one parse, in report order. */
    private static List<Diagnostic> lexicalDiagnostics(String text) {
        SourceFile file = source(text);
        SolvikParseResult result = SolvikParser.parse(file);
        assertThat(result.isSuccess()).as("input containing a lexical error must be rejected").isFalse();
        List<Diagnostic> out = new ArrayList<>();
        for (Diagnostic d : result.diagnostics().all()) {
            if (d.code() == DiagnosticCode.LEXER_ERROR) {
                out.add(d);
            }
        }
        assertThat(out).as("expected at least one SOLV-LEX-001 in " + result.diagnostics().all()).isNotEmpty();
        return out;
    }

    /** Asserts one diagnostic points at {@code expectedSlice} and displays as {@code expectedLocation}. */
    private static void assertPointsAt(Diagnostic diagnostic, String text, String expectedSlice, int expectedStart, int expectedEnd, String expectedLocation) {
        assertThat(diagnostic.span()).isEqualTo(SourceSpan.of(expectedStart, expectedEnd));
        assertThat(text.substring(diagnostic.span().startOffset(), diagnostic.span().endOffset())).isEqualTo(expectedSlice);
        assertThat(source(text).formatLocation(diagnostic.span())).isEqualTo("spans.sol:" + expectedLocation);
    }

    // ---- A single offending character, one terminator style at a time -----------------------------

    @Test
    public void lineFeedSourceLocatesTheOffendingCharacter() {
        String text = "func f(): Unit {\n    @\n}\n";
        List<Diagnostic> diagnostics = lexicalDiagnostics(text);
        assertThat(diagnostics).hasSize(1);
        int at = text.indexOf(BAD_CHARACTER);
        assertPointsAt(diagnostics.get(0), text, BAD_CHARACTER, at, at + 1, "2:5");
    }

    @Test
    public void carriageReturnLineFeedSourceLocatesTheOffendingCharacter() {
        String text = "func f(): Unit {\r\n    @\r\n}\r\n";
        List<Diagnostic> diagnostics = lexicalDiagnostics(text);
        assertThat(diagnostics).hasSize(1);
        int at = text.indexOf(BAD_CHARACTER);
        assertPointsAt(diagnostics.get(0), text, BAD_CHARACTER, at, at + 1, "2:5");
    }

    @Test
    public void loneCarriageReturnSourceLocatesTheOffendingCharacter() {
        // The case ANTLR's own line/column cannot express: its counter never leaves line 1, so the
        // span must come from the lexer's reported failing offset instead.
        String text = "func f(): Unit {\r    @\r}\r";
        List<Diagnostic> diagnostics = lexicalDiagnostics(text);
        assertThat(diagnostics).hasSize(1);
        int at = text.indexOf(BAD_CHARACTER);
        assertPointsAt(diagnostics.get(0), text, BAD_CHARACTER, at, at + 1, "2:5");
    }

    @Test
    public void allThreeTerminatorStylesAgreeOnOffsetAndDisplayPosition() {
        String lf = "func f(): Unit {\n    @\n}\n";
        String crlf = lf.replace("\n", "\r\n");
        String cr = lf.replace("\n", "\r");
        String body = lf;
        for (String text : List.of(body, crlf, cr)) {
            List<Diagnostic> diagnostics = lexicalDiagnostics(text);
            assertThat(diagnostics).as("terminator style " + style(text)).hasSize(1);
            Diagnostic d = diagnostics.get(0);
            String slice = text.substring(d.span().startOffset(), d.span().endOffset());
            assertThat(slice).as("span must select the bad character for " + style(text)).isEqualTo(BAD_CHARACTER);
            assertThat(source(text).formatLocation(d.span())).as("display position for " + style(text)).endsWith(":2:5");
        }
    }

    private static String style(String text) {
        if (text.contains("\r\n")) {
            return "CRLF";
        }
        return text.contains("\r") ? "CR" : "LF";
    }

    // ---- Several errors in one file ---------------------------------------------------------------

    @Test
    public void severalErrorsOnSeparateLineFeedLinesGetDistinctOrderedSpans() {
        String text = "@\n@\n@\n";
        List<Diagnostic> diagnostics = lexicalDiagnostics(text);
        assertThat(diagnostics).hasSize(3);
        assertThat(spansOf(diagnostics)).containsExactly(SourceSpan.of(0, 1), SourceSpan.of(2, 3), SourceSpan.of(4, 5));
    }

    @Test
    public void severalErrorsOnSeparateLoneCarriageReturnLinesGetDistinctOrderedSpans() {
        // Under the derived-position path these collapse onto the same clamped offset, which is how
        // the bug hid: two of the three diagnostics pointed at the same character.
        String text = "@\r@\r@\r";
        List<Diagnostic> diagnostics = lexicalDiagnostics(text);
        assertThat(diagnostics).hasSize(3);
        assertThat(spansOf(diagnostics)).containsExactly(SourceSpan.of(0, 1), SourceSpan.of(2, 3), SourceSpan.of(4, 5));
        assertThat(displayPositions(diagnostics, text)).containsExactly("spans.sol:1:1", "spans.sol:2:1", "spans.sol:3:1");
    }

    @Test
    public void anErrorDeepInALoneCarriageReturnFileIsNotClampedToTheFirstLine() {
        String text = "val a = 1\rval b = 2\rval @ = 3\r";
        List<Diagnostic> diagnostics = lexicalDiagnostics(text);
        assertThat(diagnostics).hasSize(1);
        Diagnostic d = diagnostics.get(0);
        int at = text.indexOf(BAD_CHARACTER);
        assertPointsAt(d, text, BAD_CHARACTER, at, at + 1, "3:5");
    }

    private static List<SourceSpan> spansOf(List<Diagnostic> diagnostics) {
        List<SourceSpan> out = new ArrayList<>();
        for (Diagnostic d : diagnostics) {
            out.add(d.span());
        }
        return out;
    }

    private static List<String> displayPositions(List<Diagnostic> diagnostics, String text) {
        List<String> out = new ArrayList<>();
        for (Diagnostic d : diagnostics) {
            out.add(source(text).formatLocation(d.span()));
        }
        return out;
    }

    // ---- Mixed lexer and parser diagnostics in one lone-CR file -------------------------------------

    @Test
    public void lexicalAndParserDiagnosticsCoexistInALoneCarriageReturnFile() {
        // The bad character ends the statement, so the following `=` is a separate parser error; the
        // lexer span must be exact while the parser span keeps its own token-derived position.
        String text = "val a = 1\rval b = 2\rval @ = 3\r";
        SourceFile file = source(text);
        SolvikParseResult result = SolvikParser.parse(file);
        Diagnostic lexerError = null;
        Diagnostic parserError = null;
        for (Diagnostic d : result.diagnostics().all()) {
            if (d.code() == DiagnosticCode.LEXER_ERROR) {
                lexerError = d;
            } else if (d.code() == DiagnosticCode.PARSER_UNEXPECTED_TOKEN) {
                parserError = d;
            }
        }
        assertThat(lexerError).isNotNull();
        assertThat(parserError).isNotNull();
        int at = text.indexOf(BAD_CHARACTER);
        assertThat(lexerError.span()).isEqualTo(SourceSpan.of(at, at + 1));
        assertThat(text.substring(lexerError.span().startOffset(), lexerError.span().endOffset())).isEqualTo(BAD_CHARACTER);
        // The parser error sits on the `=` that follows the bad character, on the same physical line.
        int equals = text.indexOf('=', at);
        assertThat(parserError.span()).isEqualTo(SourceSpan.of(equals, equals + 1));
        assertThat(text.substring(parserError.span().startOffset(), parserError.span().endOffset())).isEqualTo("=");
        assertThat(file.formatLocation(lexerError.span())).isEqualTo("spans.sol:3:5");
        assertThat(file.formatLocation(parserError.span())).isEqualTo("spans.sol:3:7");
    }

    // ---- Other lexical diagnostics keep their spans -----------------------------------------------

    @Test
    public void unterminatedStringSpanStartsAtItsOpeningQuote() {
        String text = "func f(): Unit {\n    val s = \"oops;\n}\n";
        SourceFile file = source(text);
        SolvikParseResult result = SolvikParser.parse(file);
        Diagnostic d = firstWithCode(result, DiagnosticCode.LEXER_ERROR);
        int quote = text.indexOf("\"oops;");
        assertThat(d.span()).isEqualTo(SourceSpan.of(quote, quote + 1));
        assertThat(text.substring(d.span().startOffset(), d.span().endOffset())).isEqualTo("\"");
        assertThat(file.formatLocation(d.span())).isEqualTo("spans.sol:2:13");
    }

    @Test
    public void multiCharacterLiteralSpansCoverBothReportedFailures() {
        String text = "func f(): Unit {\n    val c = 'ab';\n}\n";
        SourceFile file = source(text);
        SolvikParseResult result = SolvikParser.parse(file);
        List<Diagnostic> lexical = new ArrayList<>();
        for (Diagnostic d : result.diagnostics().all()) {
            if (d.code() == DiagnosticCode.LEXER_ERROR) {
                lexical.add(d);
            }
        }
        assertThat(lexical).hasSize(2);
        assertThat(text.substring(lexical.get(0).span().startOffset(), lexical.get(0).span().endOffset())).isEqualTo("'");
        assertThat(text.substring(lexical.get(1).span().startOffset(), lexical.get(1).span().endOffset())).isEqualTo("'");
        assertThat(spansOf(lexical)).containsExactly(SourceSpan.of(29, 30), SourceSpan.of(32, 33));
    }

    @Test
    public void unterminatedRawStringKeepsItsOpeningDelimiterSpan() {
        // The raw-string diagnostic is a distinct code positioned at the opening delimiter, and it
        // names the exact closing delimiter that was expected.
        String text = "func f(): Unit {\n    val s = r#\"abc;\n}\n";
        SourceFile file = source(text);
        SolvikParseResult result = SolvikParser.parse(file);
        Diagnostic d = firstWithCode(result, DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING);
        assertThat(d.span()).isEqualTo(SourceSpan.of(29, 32));
        assertThat(text.substring(d.span().startOffset(), d.span().endOffset())).isEqualTo("r#\"");
        assertThat(d.expected()).contains("\"#");
        assertThat(d.code().stableCode()).isEqualTo("SOLV-LEX-002");
    }

    @Test
    public void byteOrderMarkAtTheHeadOfAFileIsDiagnosedAtOffsetZero() {
        String text = "\uFEFFfunc f(): Unit {\n}\n";
        SourceFile file = source(text);
        SolvikParseResult result = SolvikParser.parse(file);
        Diagnostic d = firstWithCode(result, DiagnosticCode.LEXER_ERROR);
        assertThat(d.span()).isEqualTo(SourceSpan.of(0, 1));
        assertThat(file.formatLocation(d.span())).isEqualTo("spans.sol:1:1");
    }

    @Test
    public void everyLexicalDiagnosticSpanSelectsASingleOffendingCharacter() {
        // A one-character span is the invariant these diagnostics rely on for a caret-style display,
        // regardless of which reporting path produced the span.
        String[] inputs = {"@", "@@@", "a@b", "func f(): Unit {\n @\n}\n", "func f(): Unit {\r @\r}\r", "\"abc", "'AB'", "\uFEFFval x = 1\n"};
        for (String text : inputs) {
            for (Diagnostic d : lexicalDiagnostics(text)) {
                assertThat(d.span().length()).as("span length for input " + style(text) + " in " + text.replace("\n", "\\n").replace("\r", "\\r")).isEqualTo(1);
            }
        }
    }

    @Test
    public void anUnsupportedEscapeIsNotAParseLayerDiagnostic() {
        // The lexer admits any single escape body, so `\q` is a well-formed literal at this stage and
        // the parser reports nothing. SOLV-LEX-003 is raised against the whole literal by the
        // semantic layer (see SolvikExecutionNegativeTest), which is why the one-character
        // lexical-span rule above does not apply to it.
        String text = "func f(): Unit {\n    val s = \"bad\\q\";\n}\n";
        SolvikParseResult result = SolvikParser.parse(source(text));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.diagnostics().all()).isEmpty();
    }

    private static Diagnostic firstWithCode(SolvikParseResult result, DiagnosticCode code) {
        for (Diagnostic d : result.diagnostics().all()) {
            if (d.code() == code) {
                return d;
            }
        }
        throw new AssertionError("no " + code + " diagnostic in " + result.diagnostics().all());
    }
}
