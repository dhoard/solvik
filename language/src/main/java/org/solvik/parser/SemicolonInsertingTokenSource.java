/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.parser;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenFactory;
import org.antlr.v4.runtime.TokenSource;
import org.antlr.v4.runtime.misc.Pair;
import org.solvik.parser.generated.SolvikLexer;

/**
 * The "Semicolon-Inserting Token Stream" stage of {@code docs/ARCHITECTURE.md}: a
 * {@link TokenSource} wrapper implementing the Go-style lexical semicolon insertion of
 * {@code docs/LANGUAGE_SPEC.md} section 16 between the Solvik lexer and the ANTLR parser.
 *
 * <p>The Solvik lexer keeps every physical newline as a hidden {@code NEWLINE} token and hides
 * comment bodies instead of skipping them, because a newline inside a comment is still a physical
 * newline. This stage observes that raw token sequence and, at a line boundary, injects one
 * synthetic {@code SEMI} token when all specification conditions hold:
 * <ol>
 * <li>the unmatched {@code (} and {@code [} nesting depths are both zero (curly braces are
 * deliberately not tracked, so a newline after a block-closing brace can terminate an expression
 * statement);</li>
 * <li>the preceding significant token is an identifier, a literal, {@code break}, {@code continue},
 * {@code return}, {@code )}, {@code ]}, or {@code }} &mdash; see {@link #isNewlineTerminator};</li>
 * <li>the next significant token is not {@code .}, {@code ?.}, or {@code else} &mdash; see
 * {@link #isMemberChainContinuation}. This is the only lookahead exception; no JavaScript-style
 * heuristics are used.</li>
 * </ol>
 *
 * <p>A run of newlines, blank lines, and comment newlines between two significant tokens is a
 * single boundary carrying at most one synthetic semicolon, so consecutive blank lines never
 * duplicate an insertion, and a preceding explicit {@code ;} suppresses the following boundary
 * because {@code SEMI} is not itself an eligible terminator. End of file closes the last physical
 * line and applies the same rule without the continuation exception, so a final eligible token is
 * terminated exactly once whether or not the file ends with a newline.
 *
 * <p>Purity contract: the decision reads only the raw token sequence produced by the lexer. This
 * class references no parser type, no error strategy, and no diagnostic; insertion is therefore
 * deterministic and independent of parser errors and recovery, as the language specification
 * requires. Explicit {@code ;} and synthesized {@code ;} carry the grammar's single {@code SEMI}
 * token type on the default channel, so the parser consumes them identically. Consumed
 * {@code NEWLINE} tokens are never delivered; comments pass through on their original hidden
 * channel, and the continuation lookahead skips them while scanning ahead for the next significant
 * token.
 *
 * <p>Synthetic tokens carry a zero-width placement in real source coordinates: {@code text} is
 * {@code ";"}, {@code startIndex} is the offset immediately after the terminated token,
 * {@code stopIndex = startIndex - 1} matches the grammar's inclusive-stop convention for an empty
 * token (so span construction ends exactly at the terminated token's last character), and the
 * line/column locate the physical newline that triggered the insertion, falling back to the
 * end-of-file position when end of file itself closed the line. Spans built by the AST builder
 * therefore end immediately after a statement's last real token, never at the following newline.
 *
 * <p>Instances wrap one lexer for one parse and are not thread-safe.
 */
public final class SemicolonInsertingTokenSource implements TokenSource {

    /** A {@code SEMI} token created by this stage; its class distinguishes it from lexer tokens. */
    private static final class SyntheticSemiToken extends CommonToken {
        SyntheticSemiToken(TokenSource lexer, int offset, int line, int charPositionInLine) {
            super(new Pair<>(lexer, lexer.getInputStream()), SolvikLexer.SEMI, Token.DEFAULT_CHANNEL, offset, offset - 1);
            setText(";");
            setLine(line);
            setCharPositionInLine(charPositionInLine);
        }
    }

    /**
     * Token types whose trailing line boundary may terminate a statement (specification
     * condition 2). {@code break} and {@code continue} join the table when the Phase 4 loop
     * syntax and AST nodes land. {@code this} joins the table in Phase 6: it is a value-producing
     * atom exactly like an identifier or literal, so a statement ending in {@code this} must
     * terminate. Phase 7's {@code Long}, floating-point, and {@code Char} literals join for the
     * same reason. Brackets already participate because insertion must track their nesting for
     * condition 1. Phase 8's {@code interface} and {@code implements} keywords deliberately do not
     * join: like {@code class} and {@code extends} they open a construct, and an interface member
     * signature already ends in {@code ;}, so no new terminating token is needed. Phase 10's
     * {@code null} literal joins for the same reason as the other literals, and {@code ?} joins
     * because it completes a written nullable type reference; the new keywords {@code is} and
     * {@code as} and the coalescing operator {@code ??} are not terminators.
     */
    private static final int[] NEWLINE_TERMINATORS = { //
            SolvikLexer.Identifier, //
            SolvikLexer.INT_LITERAL, //
            SolvikLexer.LONG_LITERAL, //
            SolvikLexer.FLOATING_LITERAL, //
            SolvikLexer.STRING_LITERAL, //
            SolvikLexer.RAW_STRING_LITERAL, //
            SolvikLexer.CHAR_LITERAL, //
            SolvikLexer.BOOL_LITERAL, //
            SolvikLexer.NULL, //
            SolvikLexer.QUESTION, //
            SolvikLexer.THIS, //
            SolvikLexer.BREAK, //
            SolvikLexer.CONTINUE, //
            SolvikLexer.RETURN, //
            SolvikLexer.RPAREN, //
            SolvikLexer.RBRACKET, //
            SolvikLexer.RBRACE, //
    };

    /**
     * Significant tokens that continue the previous line when they follow a line boundary
     * (specification condition 3). Leading {@code .} enables member chains; {@code ?.} is the safe
     * member-access operator from Phase 10 and is treated identically. The specification's list is
     * exhaustive, so {@code is}, {@code as}, {@code ?}, and {@code ??} do not continue a line.
     */
    private static final int[] CONTINUATION_TOKENS = { //
            SolvikLexer.DOT, //
            SolvikLexer.NULLABLE_DOT, //
            SolvikLexer.ELSE, //
    };

    private final TokenSource lexer;
    /** Already-rewritten tokens waiting for delivery (an injected semicolon precedes its trigger). */
    private final Deque<Token> pending = new ArrayDeque<>();
    /** Last delivered significant token; comments and newlines never replace it. */
    private Token previousSignificant;
    private int unmatchedParenDepth;
    private int unmatchedBracketDepth;
    /** True while a line boundary (possibly many newlines/comments deep) awaits its next token. */
    private boolean boundaryPending;
    private int boundaryLine;
    private int boundaryColumn;
    /** End of file is processed as a boundary exactly once, even though lexers re-deliver EOF. */
    private boolean eofProcessed;

    public SemicolonInsertingTokenSource(TokenSource lexer) {
        this.lexer = Objects.requireNonNull(lexer, "lexer");
    }

    /**
     * Whether a line boundary directly after a token of this type may terminate the statement.
     * Exposed so tests pin the table to the specification instead of example behavior alone.
     */
    public static boolean isNewlineTerminator(int tokenType) {
        for (int t : NEWLINE_TERMINATORS) {
            if (t == tokenType) {
                return true;
            }
        }
        return false;
    }

    /** Whether a token directly after a line boundary must continue the previous line. */
    public static boolean isMemberChainContinuation(int tokenType) {
        for (int t : CONTINUATION_TOKENS) {
            if (t == tokenType) {
                return true;
            }
        }
        return false;
    }

    /** Whether a token was injected by this stage rather than produced by the lexer. */
    public static boolean isSyntheticSemi(Token token) {
        return token instanceof SyntheticSemiToken;
    }

    @Override
    public Token nextToken() {
        if (!pending.isEmpty()) {
            return pending.poll();
        }
        return fetchRewritten();
    }

    /** Pulls lexer tokens until one (possibly preceded by an injected semicolon) can be returned. */
    private Token fetchRewritten() {
        while (true) {
            Token t = lexer.nextToken();
            int type = t.getType();

            if (type == Token.EOF) {
                // End of file closes the final physical line: the same boundary rule without the
                // continuation exception, and without requiring a preceding newline. Once consumed,
                // EOF never triggers a second insertion even though lexers re-deliver it.
                boolean atBoundary = boundaryPending;
                if (!eofProcessed && terminatesAt(previousSignificant) && atZeroNesting()) {
                    eofProcessed = true;
                    int line = atBoundary ? boundaryLine : t.getLine();
                    int column = atBoundary ? boundaryColumn : t.getCharPositionInLine();
                    return new SyntheticSemiToken(lexer, previousSignificant.getStopIndex() + 1, line, column);
                }
                eofProcessed = true;
                return t;
            }

            if (type == SolvikLexer.NEWLINE) {
                // Physical newline. The NEWLINE token itself is consumed here and never delivered.
                markBoundary(t.getLine(), t.getCharPositionInLine());
                continue;
            }
            if (type == SolvikLexer.LINE_COMMENT || type == SolvikLexer.BLOCK_COMMENT) {
                // Whitespace for parsing, but a newline inside the comment body is a physical
                // newline. The boundary remains pending because the continuation exception looks
                // ahead to the next significant token, not to a comment.
                if (containsNewline(t.getText())) {
                    markBoundary(t.getLine(), t.getCharPositionInLine());
                }
                return t;
            }

            // Significant token: resolve the pending boundary before this token becomes the new
            // predecessor. The injected semicolon is returned first and queues this token behind it.
            Token terminated = previousSignificant;
            boolean insert = boundaryPending && terminatesAt(terminated) && atZeroNesting() && !isMemberChainContinuation(type);
            switch (type) {
                case SolvikLexer.LPAREN:
                    unmatchedParenDepth++;
                    break;
                case SolvikLexer.RPAREN:
                    if (unmatchedParenDepth > 0) {
                        unmatchedParenDepth--;
                    }
                    break;
                case SolvikLexer.LBRACKET:
                    unmatchedBracketDepth++;
                    break;
                case SolvikLexer.RBRACKET:
                    if (unmatchedBracketDepth > 0) {
                        unmatchedBracketDepth--;
                    }
                    break;
                default:
                    break;
            }
            previousSignificant = t;
            boundaryPending = false;
            if (insert) {
                Token semi = new SyntheticSemiToken(lexer, terminated.getStopIndex() + 1, boundaryLine, boundaryColumn);
                pending.add(t);
                return semi;
            }
            return t;
        }
    }

    /** Specification condition 1: no `(` or `[` nesting is currently open. */
    private boolean atZeroNesting() {
        return unmatchedParenDepth == 0 && unmatchedBracketDepth == 0;
    }

    private static boolean terminatesAt(Token previousSignificant) {
        return previousSignificant != null && isNewlineTerminator(previousSignificant.getType());
    }

    private void markBoundary(int line, int column) {
        if (!boundaryPending) {
            boundaryLine = line;
            boundaryColumn = column;
        }
        boundaryPending = true;
    }

    private static boolean containsNewline(String text) {
        return text != null && (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0);
    }

    @Override
    public int getLine() {
        return lexer.getLine();
    }

    @Override
    public int getCharPositionInLine() {
        return lexer.getCharPositionInLine();
    }

    @Override
    public CharStream getInputStream() {
        return lexer.getInputStream();
    }

    @Override
    public String getSourceName() {
        return lexer.getSourceName();
    }

    @Override
    public void setTokenFactory(TokenFactory<?> factory) {
        lexer.setTokenFactory(factory);
    }

    @Override
    public TokenFactory<?> getTokenFactory() {
        return lexer.getTokenFactory();
    }
}
