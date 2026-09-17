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
package org.solvik.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.source.SourceFile;
import org.solvik.source.SourceSpan;

/**
 * Converts lexer and parser error callbacks into Solvik diagnostics with authoritative character
 * spans. ANTLR's reported line/column values are converted back to offsets only as a fallback
 * when a callback carries no token; token offsets are preferred.
 *
 * <p>A lexer callback ({@code recognizer instanceof Lexer}) becomes {@link
 * DiagnosticCode#LEXER_ERROR} positioned at the failing character. The one exception is an
 * {@link UnterminatedRawStringException}, which becomes {@link
 * DiagnosticCode#LEXER_UNTERMINATED_RAW_STRING} positioned at the opening delimiter and recording
 * the expected closing delimiter.
 *
 * <p>SimpleLanguage-only syntax is classified separately ({@link
 * DiagnosticCode#PARSER_UNSUPPORTED_LEGACY_SYNTAX}) so removal regressions can assert why a
 * program was rejected. Classification inspects only the offending token spelling plus the
 * expectation set ANTLR reports; it never relies on an alternate parse path.
 */
final class SolvikErrorListener extends BaseErrorListener {

    /** Spellings that exist only in SimpleLanguage syntax and are never valid Solvik tokens. */
    private static final Set<String> LEGACY_KEYWORDS = Set.of("function");

    private final List<Diagnostic> collected = new ArrayList<>();
    private final SourceFile source;

    SolvikErrorListener(SourceFile source) {
        this.source = source;
    }

    boolean hasErrors() {
        return !collected.isEmpty();
    }

    DiagnosticBag build() {
        DiagnosticBag.Builder builder = DiagnosticBag.builder();
        for (Diagnostic d : collected) {
            builder.add(d);
        }
        return builder.build();
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
        if (e instanceof UnterminatedRawStringException raw) {
            collected.add(Diagnostic.expectedFound(
                    DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING,
                    spanOf(raw.getOffendingToken()),
                    "unterminated raw string literal; expected closing delimiter " + raw.expectedClosingDelimiter(),
                    raw.expectedClosingDelimiter(),
                    describe(raw.getOffendingToken())));
            return;
        }
        if (!(recognizer instanceof org.antlr.v4.runtime.Parser)) {
            // Lexical failure: offending symbol is absent; position comes from the callback.
            collected.add(Diagnostic.error(DiagnosticCode.LEXER_ERROR, offsetSpan(line, charPositionInLine), "invalid character sequence: " + msg));
            return;
        }
        Token token = offendingSymbol instanceof Token t ? t : null;
        Token legacy = legacyToken(recognizer, token);
        if (legacy != null) {
            collected.add(Diagnostic.expectedFound(
                    DiagnosticCode.PARSER_UNSUPPORTED_LEGACY_SYNTAX,
                    spanOf(legacy),
                    "unsupported SimpleLanguage syntax; Solvik has no SimpleLanguage compatibility mode",
                    "'func'",
                    describe(legacy)));
            return;
        }
        boolean atEof = token == null || token.getType() == Token.EOF;
        DiagnosticCode code = atEof ? DiagnosticCode.PARSER_INCOMPLETE_INPUT : DiagnosticCode.PARSER_UNEXPECTED_TOKEN;
        String message = (atEof ? "incomplete input: " : "unexpected input: ") + msg;
        collected.add(Diagnostic.error(code, spanOf(token), message));
    }

    /**
     * Returns the legacy {@code function} token this error should be attributed to, or {@code null}.
     * A SimpleLanguage declaration begins with {@code function}; because {@code function} is a legal
     * Solvik identifier, an executable-top-level statement may start with it and the parser then
     * rejects the following identifier (for example {@code function foo(x)}). When the error's
     * offending token immediately follows a {@code function} identifier on the same line, the error
     * is attributed to that {@code function} token so the removal regression still sees
     * {@code SOLV-PARS-004}. Otherwise the offending token itself is checked for the case where
     * {@code function} is unexpected directly and {@code func} was expected.
     */
    private static Token legacyToken(Recognizer<?, ?> recognizer, Token token) {
        if (token == null) {
            return null;
        }
        if (LEGACY_KEYWORDS.contains(token.getText())) {
            return token;
        }
        if (recognizer instanceof org.antlr.v4.runtime.Parser parser && token.getTokenIndex() > 0) {
            Token previous = parser.getInputStream().get(token.getTokenIndex() - 1);
            if (previous != null && previous.getChannel() == Token.DEFAULT_CHANNEL && LEGACY_KEYWORDS.contains(previous.getText())) {
                return previous;
            }
        }
        return null;
    }

    private static String describe(Token token) {
        return token == null || token.getType() == Token.EOF ? "end of input" : "'" + token.getText() + "'";
    }

    /** Character span of the offending token; EOF becomes a zero-length span at end of input. */
    private SourceSpan spanOf(Token token) {
        if (token == null || token.getType() == Token.EOF || token.getStartIndex() < 0) {
            int end = source.textLength();
            return SourceSpan.of(source.id(), end, end);
        }
        int start = token.getStartIndex();
        int stop = Math.max(token.getStopIndex(), start - 1);
        return SourceSpan.of(source.id(), start, Math.min(stop + 1, source.textLength()));
    }

    /** One-character span derived from ANTLR callback coordinates (fallback path only). */
    private SourceSpan offsetSpan(int line, int charPositionInLine) {
        int offset = Math.min(source.offsetAt(line, charPositionInLine + 1), source.textLength());
        int end = Math.min(offset + 1, source.textLength());
        return SourceSpan.of(source.id(), offset, end);
    }
}
