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
 * The "line-boundary token stream" stage of {@code docs/ARCHITECTURE.md}: a {@link TokenSource}
 * wrapper that turns the lexer's hidden physical newlines into the parser-visible {@code NEWLINE}
 * tokens on which the physical-line rules of {@code docs/LANGUAGE_SPEC.md} section 16 are written.
 *
 * <p>The Solvik lexer keeps every physical newline as a hidden {@code NEWLINE} token and hides
 * comment bodies instead of skipping them, because a newline inside a comment is still a physical
 * newline. This stage observes that raw sequence and, at a line boundary, forwards one
 * {@code NEWLINE} token on the default channel when both of these hold:
 * <ol>
 * <li>the preceding significant token ends a line &mdash; an identifier, a literal, {@code break},
 * {@code continue}, {@code return}, an explicit {@code ;}, {@code )}, {@code ]}, {@code }}, or a
 * completed {@code ?} propagation; see {@link #endsLine};</li>
 * <li>end of file, which closes the final physical line and applies the same rule without requiring a
 * following token.</li>
 * </ol>
 *
 * <p>Nesting depth is deliberately not consulted. A boundary is placed at every line break the
 * language admits a separator at, including inside parentheses and brackets, so a statement inside a
 * bracketed body terminates exactly like a statement anywhere else. There is equally no lookahead
 * exception for the token that follows: a line break that continues an expression - after a binary
 * operator, after a comma, before a {@code .} or a {@code ::} - still forwards its {@code NEWLINE},
 * and the grammar absorbs it because {@code Solvik.g4} writes {@code NEWLINE*} only at the positions
 * where section 16 permits a line to continue. That is the whole point of the arrangement: the
 * grammar, not a heuristic table, decides which continuations exist, so a line break where the
 * specification allows none is a parse error at the break rather than a silently joined expression.
 *
 * <p>A run of newlines, blank lines, and comment newlines between two significant tokens is a single
 * boundary carrying at most one {@code NEWLINE}, so blank lines never duplicate a boundary. An
 * explicit {@code ;} ends its line, so a program that writes one at the end of a line gets the one
 * boundary that follows it rather than a separator and a boundary in a shape the grammar would have
 * to ignore; {@code ;} between two constructs of one physical line stays a separator and never
 * becomes a boundary, because no physical newline separates them. Boundary placement is not a
 * permission: a {@code ;} that ends a line, ends the file, or precedes a stand-alone closing brace
 * separates nothing, and {@link PhysicalLineRules} rejects it as {@code SOLV-PARS-012}.
 *
 * <p>Purity contract: the decision reads only the raw token sequence the lexer produces. This class
 * references no parser type, no error strategy, and no diagnostic, so boundary placement is
 * deterministic and independent of parser errors and recovery, as the language specification
 * requires. Consumed boundaries are never delivered twice; comments pass through on their original
 * hidden channel.
 *
 * <p>A boundary token carries the position of the first physical newline of its run, so its start
 * offset is the first character of the break and its line and column locate that character. A
 * boundary synthesized at end of file is zero-width and sits immediately after the token it
 * terminates. Spans built by the AST builder therefore end at a statement's last real token, never at
 * the following newline.
 *
 * <p>Instances wrap one lexer for one parse and are not thread-safe.
 */
public final class PhysicalLineTokenSource implements TokenSource {

    /** A boundary this stage placed on the default channel; its class distinguishes it from lexer tokens. */
    private static final class LineBoundaryToken extends CommonToken {
        LineBoundaryToken(TokenSource source, int start, int stop, int line, int column) {
            super(new Pair<>(source, source.getInputStream()), SolvikLexer.NEWLINE, Token.DEFAULT_CHANNEL, start, stop);
            setText("\n");
            setLine(line);
            setCharPositionInLine(column);
        }
    }

    /**
     * Token types whose trailing physical line ends there (condition 2). {@code break},
     * {@code continue}, and {@code return} are terminators because each can be the last token of a
     * statement: {@code return} alone on a line returns {@code Unit}. {@code this} is a
     * value-producing atom exactly like an identifier or a literal. {@code ?} completes a written
     * nullable type reference or a propagation suffix. Brackets and braces participate because a line
     * may legitimately end on one. The keywords that open a construct - {@code var}, {@code mutable},
     * {@code func}, {@code class}, {@code if}, {@code while}, {@code for}, {@code switch}, {@code
     * case}, {@code try}, {@code else}, {@code and every other keyword that must be followed by what
     * it modifies} - are absent from this table, which is what makes a line break after one of them a
     * continuation rather than a terminator.
     */
    private static final int[] LINE_ENDINGS = { //
            SolvikLexer.Identifier, //
            SolvikLexer.INTEGER_LITERAL, //
            SolvikLexer.LONG_LITERAL, //
            SolvikLexer.FLOATING_LITERAL, //
            SolvikLexer.STRING_LITERAL, //
            SolvikLexer.RAW_STRING_LITERAL, //
            SolvikLexer.CHARACTER_LITERAL, //
            SolvikLexer.BOOL_LITERAL, //
            SolvikLexer.NULL, //
            SolvikLexer.QUESTION, //
            SolvikLexer.THIS, //
            SolvikLexer.SUPER, //
            SolvikLexer.BREAK, //
            SolvikLexer.CONTINUE, //
            SolvikLexer.RETURN, //
            SolvikLexer.RPAREN, //
            SolvikLexer.RBRACKET, //
            SolvikLexer.RBRACE, //
            SolvikLexer.SEMI, //
    };

    private final TokenSource lexer;
    /** Already-rewritten tokens waiting for delivery (a boundary precedes the token that resolved it). */
    private final Deque<Token> pending = new ArrayDeque<>();
    /** Last delivered significant token; comments and newlines never replace it. */
    private Token previousSignificant;
    /** True while a line boundary (possibly many newlines and comment lines deep) awaits its next token. */
    private boolean boundaryPending;
    private int boundaryStart;
    private int boundaryStop;
    private int boundaryLine;
    private int boundaryColumn;
    /** End of file is processed as a boundary exactly once, even though lexers re-deliver EOF. */
    private boolean eofProcessed;

    public PhysicalLineTokenSource(TokenSource lexer) {
        this.lexer = Objects.requireNonNull(lexer, "lexer");
    }

    /**
     * Whether a physical line directly after a token of this type ends there. Exposed so tests pin
     * the table to the specification instead of example behavior alone.
     *
     * @param tokenType a lexer token type
     * @return whether the token may be the last significant token of a physical line
     */
    public static boolean endsLine(int tokenType) {
        for (int t : LINE_ENDINGS) {
            if (t == tokenType) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a {@code NEWLINE} token on the default channel was placed by this stage rather than
     * delivered by the lexer.
     *
     * @param token a token from the stream this stage produces
     * @return whether this stage moved it onto the default channel
     */
    public static boolean isLineBoundary(Token token) {
        return token instanceof LineBoundaryToken;
    }

    @Override
    public Token nextToken() {
        if (!pending.isEmpty()) {
            return pending.poll();
        }
        return fetchRewritten();
    }

    /** Pulls lexer tokens until one (possibly preceded by a boundary) can be returned. */
    private Token fetchRewritten() {
        while (true) {
            Token token = lexer.nextToken();
            int type = token.getType();

            if (type == Token.EOF) {
                // End of file closes the final physical line, so the last line is terminated exactly
                // once whether or not the file ends with a newline. Once consumed, EOF never triggers
                // a second boundary even though lexers re-deliver it.
                if (!eofProcessed && endsAt(previousSignificant)) {
                    eofProcessed = true;
                    return new LineBoundaryToken(lexer, previousSignificant.getStopIndex() + 1, previousSignificant.getStopIndex(), //
                            boundaryPending ? boundaryLine : token.getLine(), boundaryPending ? boundaryColumn : token.getCharPositionInLine());
                }
                eofProcessed = true;
                return token;
            }

            if (type == SolvikLexer.NEWLINE) {
                // A physical newline. The token itself is consumed here; the boundary it opens is
                // delivered only if a later token proves the line ended. The first newline of a run
                // fixes the boundary's position, so blank lines do not move it.
                markBoundary(token);
                continue;
            }
            if (type == SolvikLexer.LINE_COMMENT || type == SolvikLexer.BLOCK_COMMENT) {
                // Whitespace for parsing, but a newline inside the comment body is a physical newline.
                // The boundary stays pending because a comment neither ends nor continues a line.
                if (containsNewline(token.getText())) {
                    markBoundary(token);
                }
                return token;
            }

            // Significant token: resolve the pending boundary before this token becomes the new
            // predecessor. The boundary is returned first and queues this token behind it.
            boolean boundary = boundaryPending && endsAt(previousSignificant);
            int start = boundaryStart;
            int stop = boundaryStop;
            int line = boundaryLine;
            int column = boundaryColumn;
            previousSignificant = token;
            boundaryPending = false;
            if (boundary) {
                pending.add(token);
                return new LineBoundaryToken(lexer, start, stop, line, column);
            }
            return token;
        }
    }

    private static boolean endsAt(Token previous) {
        return previous != null && endsLine(previous.getType());
    }

    private void markBoundary(Token newline) {
        if (!boundaryPending) {
            boundaryStart = newline.getStartIndex();
            boundaryStop = newline.getStopIndex();
            boundaryLine = newline.getLine();
            boundaryColumn = newline.getCharPositionInLine();
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
