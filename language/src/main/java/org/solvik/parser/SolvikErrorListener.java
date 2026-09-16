/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
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
        if (isLegacySyntax(msg, token)) {
            collected.add(Diagnostic.expectedFound(
                    DiagnosticCode.PARSER_UNSUPPORTED_LEGACY_SYNTAX,
                    spanOf(token),
                    "unsupported SimpleLanguage syntax; Solvik has no SimpleLanguage compatibility mode",
                    "'fun'",
                    describe(token)));
            return;
        }
        boolean atEof = token == null || token.getType() == Token.EOF;
        DiagnosticCode code = atEof ? DiagnosticCode.PARSER_INCOMPLETE_INPUT : DiagnosticCode.PARSER_UNEXPECTED_TOKEN;
        String message = (atEof ? "incomplete input: " : "unexpected input: ") + msg;
        collected.add(Diagnostic.error(code, spanOf(token), message));
    }

    private static boolean isLegacySyntax(String msg, Token token) {
        if (token == null || !LEGACY_KEYWORDS.contains(token.getText())) {
            return false;
        }
        // Only classify as legacy syntax when 'fun' was among the expected tokens.
        return msg != null && msg.contains("'fun'");
    }

    private static String describe(Token token) {
        return token == null || token.getType() == Token.EOF ? "end of input" : "'" + token.getText() + "'";
    }

    /** Character span of the offending token; EOF becomes a zero-length span at end of input. */
    private SourceSpan spanOf(Token token) {
        if (token == null || token.getType() == Token.EOF || token.getStartIndex() < 0) {
            int end = source.textLength();
            return SourceSpan.of(end, end);
        }
        int start = token.getStartIndex();
        int stop = Math.max(token.getStopIndex(), start - 1);
        return SourceSpan.of(start, Math.min(stop + 1, source.textLength()));
    }

    /** One-character span derived from ANTLR callback coordinates (fallback path only). */
    private SourceSpan offsetSpan(int line, int charPositionInLine) {
        int offset = Math.min(source.offsetAt(line, charPositionInLine + 1), source.textLength());
        int end = Math.min(offset + 1, source.textLength());
        return SourceSpan.of(offset, end);
    }
}
