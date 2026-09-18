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
import java.util.concurrent.atomic.AtomicInteger;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import org.solvik.parser.SemicolonInsertingTokenSource;
import org.solvik.parser.generated.SolvikLexer;

/**
 * Token-stream-layer tests for Go-style semicolon insertion (docs/LANGUAGE_SPEC.md section 16).
 * Each case inspects the rewritten default-channel stream from {@link
 * SemicolonInsertingTokenSource} directly without running the parser: which {@code SEMI} tokens
 * exist, which were synthesized, and exactly where they sit. Because no parser participates, these
 * tests also pin that insertion is a purely lexical decision. In renderings below, {@code ~;~}
 * marks a synthesized semicolon and plain {@code ;} an explicit one.
 */
public final class SolvikSemicolonTokenStreamTest {

    /** Delivers all tokens through EOF on the default channel; hidden comment tokens are dropped. */
    private static List<Token> delivered(String src) {
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString(src));
        lexer.removeErrorListeners();
        return delivered(lexer);
    }

    private static List<Token> delivered(SolvikLexer lexer) {
        SemicolonInsertingTokenSource stream = new SemicolonInsertingTokenSource(lexer);
        List<Token> out = new ArrayList<>();
        while (true) {
            Token t = stream.nextToken();
            if (t.getChannel() == Token.DEFAULT_CHANNEL) {
                out.add(t);
            }
            if (t.getType() == Token.EOF) {
                // Requests after exhaustion keep returning EOF and never insert again.
                assertThat(stream.nextToken().getType()).isEqualTo(Token.EOF);
                return out;
            }
        }
    }

    /** Token texts joined by spaces; synthesized semicolons render as `~;~`. */
    private static String render(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            if (SemicolonInsertingTokenSource.isSyntheticSemi(t)) {
                sb.append("~;~");
            } else if (t.getType() == Token.EOF) {
                sb.append("<EOF>");
            } else {
                sb.append(t.getText());
            }
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    private static List<Token> semis(List<Token> tokens, boolean synthetic) {
        List<Token> out = new ArrayList<>();
        for (Token t : tokens) {
            if (t.getType() == SolvikLexer.SEMI && SemicolonInsertingTokenSource.isSyntheticSemi(t) == synthetic) {
                out.add(t);
            }
        }
        return out;
    }

    private static List<Integer> offsets(List<Token> tokens) {
        List<Integer> out = new ArrayList<>();
        for (Token t : tokens) {
            out.add(t.getStartIndex());
        }
        return out;
    }

    /** Physical newlines terminate statements exactly like explicit semicolons. */
    @Test
    public void newlineFormMatchesSemicolonFormTokenForToken() {
        List<Token> viaNewline = delivered("val x = 1\nval y = 2\n");
        List<Token> viaSemicolon = delivered("val x = 1;\nval y = 2;\n");
        assertThat(render(viaNewline).replace("~;~", ";")).isEqualTo(render(viaSemicolon));
        assertThat(semis(viaSemicolon, false).size()).isEqualTo(2);
        assertThat(semis(viaSemicolon, true).size()).as("explicit form needs no synthesis").isEqualTo(0);
        assertThat(semis(viaNewline, true).size()).isEqualTo(2);
        assertThat(semis(viaNewline, false).size()).as("newline form keeps nothing explicit").isEqualTo(0);
    }

    /** A synthesized SEMI is a default-channel SEMI placed directly after the terminated token. */
    @Test
    public void syntheticSemisShareTheSemiTokenTypeAndPlacement() {
        String src = "val x = 1\nval y = 2\n";
        List<Token> injected = semis(delivered(src), true);
        assertThat(injected.size()).isEqualTo(2);
        for (Token semi : injected) {
            assertThat(semi.getType()).isEqualTo(SolvikLexer.SEMI);
            assertThat(semi.getText()).isEqualTo(";");
            assertThat(semi.getChannel()).isEqualTo(Token.DEFAULT_CHANNEL);
            // Zero-width placement: start just past the terminated token, stop = start - 1, so the
            // grammar's inclusive-stop convention yields spans ending exactly at that token.
            assertThat(semi.getStopIndex()).isEqualTo(semi.getStartIndex() - 1);
        }
        assertThat(offsets(injected)).isEqualTo(List.of(src.indexOf('1') + 1, src.indexOf('2') + 1));
        // The coordinates locate the physical newline that triggered the insertion.
        assertThat(injected.get(0).getLine()).isEqualTo(1);
        assertThat(injected.get(0).getCharPositionInLine()).isEqualTo(9);
        assertThat(injected.get(1).getLine()).isEqualTo(2);
    }

    /** Consecutive blank lines never duplicate an insertion. */
    @Test
    public void blankLinesYieldExactlyOneSemi() {
        String src = "val x = 1\n\n\n\nval y = 2";
        List<Token> toks = delivered(src);
        assertThat(render(toks)).isEqualTo("val x = 1 ~;~ val y = 2 ~;~ <EOF>");
        // The run of blank lines contributes exactly one semi; the second comes from the EOF rule.
        assertThat(offsets(semis(toks, true))).isEqualTo(List.of(src.indexOf('1') + 1, src.length()));
    }

    /** Expressions continue after operators and commas: one terminator for the whole statement. */
    @Test
    public void noInsertionAfterOperatorsOrCommasInMultilineExpressions() {
        String src = "val total = price +\n    tax +\n    shipping";
        List<Token> toks = delivered(src);
        assertThat(render(toks)).isEqualTo("val total = price + tax + shipping ~;~ <EOF>");
        assertThat(offsets(semis(toks, true))).isEqualTo(List.of(src.length()));
    }

    /** Newlines inside an unmatched `(` never terminate; balance restores insertion. */
    @Test
    public void unmatchedOpeningParenthesisSuppressesInsertion() {
        String src = "g(1,\n    2,\n    3\n)\nx";
        List<Token> toks = delivered(src);
        int close = src.indexOf(')');
        assertThat(offsets(semis(toks, true))).isEqualTo(List.of(close + 1, src.length()));
        assertThat(render(toks)).isEqualTo("g ( 1 , 2 , 3 ) ~;~ x ~;~ <EOF>");
    }

    /** Nested unmatched parentheses keep suppressing until every opener is matched. */
    @Test
    public void nestedUnmatchedParenthesesSuppressInsertion() {
        // Openers stack to depth 3 across the first newline; only the final close rebalances.
        String src = "f(g((1 + 2\n)))\nx";
        List<Token> toks = delivered(src);
        assertThat(offsets(semis(toks, true))).isEqualTo(List.of(src.lastIndexOf(')') + 1, src.length()));
        // A permanently unbalanced stream suppresses through EOF entirely.
        assertThat(semis(delivered("f(g((1 + 2\n) * 3\n)\nx"), true).size()).isEqualTo(0);
    }

    /** Newlines inside an unmatched `[` never terminate; the balanced close restores insertion. */
    @Test
    public void unmatchedOpeningBracketSuppressesInsertion() {
        String src = "g[1,\n    2,\n    3\n]\nx";
        List<Token> toks = delivered(src);
        int close = src.indexOf(']');
        assertThat(offsets(semis(toks, true))).isEqualTo(List.of(close + 1, src.length()));
        assertThat(render(toks)).isEqualTo("g [ 1 , 2 , 3 ] ~;~ x ~;~ <EOF>");
    }

    /** Parenthesis and bracket nesting both participate in condition 1. */
    @Test
    public void mixedParenAndBracketNestingSuppressesInsertion() {
        String src = "f(a[1,\n2],\n3)\nx";
        List<Token> toks = delivered(src);
        // Every newline is inside an open `(` or `[`; only the statement end and EOF terminate.
        int close = src.indexOf(')');
        assertThat(offsets(semis(toks, true))).isEqualTo(List.of(close + 1, src.length()));
    }

    /** `]` is an eligible terminator (specification condition 2). */
    @Test
    public void closingBracketTerminatesAtALineBoundary() {
        List<Token> toks = delivered("val x = a[0]\nval y = 2");
        assertThat(render(toks)).isEqualTo("val x = a [ 0 ] ~;~ val y = 2 ~;~ <EOF>");
        assertThat(SemicolonInsertingTokenSource.isNewlineTerminator(SolvikLexer.RBRACKET)).isTrue();
    }

    @Test
    public void endOfFileTerminatesFinalEligibleTokenWithoutNewline() {
        List<Token> noNewline = delivered("val x = 1");
        assertThat(semis(noNewline, true).size()).isEqualTo(1);
        assertThat(offsets(semis(noNewline, true))).isEqualTo(List.of(9));
        // A trailing newline takes the ordinary boundary path: same single semi, no EOF duplicate.
        List<Token> withNewline = delivered("val x = 1\n");
        assertThat(offsets(semis(withNewline, true))).isEqualTo(offsets(semis(noNewline, true)));
        // An explicit semicolon already terminated the statement; EOF adds nothing.
        List<Token> explicit = delivered("val x = 1;");
        assertThat(semis(explicit, true).size()).isEqualTo(0);
        assertThat(semis(explicit, false).size()).isEqualTo(1);
    }

    /** EOF inserts nothing after an ineligible token or in whitespace-only input. */
    @Test
    public void endOfFileDoesNotTerminateAfterIneligibleTokens() {
        assertThat(semis(delivered("val x ="), true).size()).isEqualTo(0);
        assertThat(semis(delivered("val x = ("), true).size()).isEqualTo(0);
        assertThat(semis(delivered(""), true).size()).isEqualTo(0);
        assertThat(semis(delivered("\n\n"), true).size()).isEqualTo(0);
    }

    /** `return` followed by a newline terminates the return statement (spec example). */
    @Test
    public void returnFollowedByNewlineTerminatesTheReturn() {
        List<Token> toks = delivered("return\nvalue");
        assertThat(render(toks)).isEqualTo("return ~;~ value ~;~ <EOF>");
        assertThat(semis(toks, true).get(0).getStartIndex()).as("`return` ends at 6, so the semi sits there").isEqualTo(6);
    }

    /** `}` followed by `else` on the next line must not terminate before the `else`. */
    @Test
    public void elseOnTheFollowingLineSuppressesInsertionAfterClosingBrace() {
        String src = "if (c) {\n} else if (d) {\n}\nelse {\n}\nz";
        List<Token> toks = delivered(src);
        // Suppressed around both `else` spellings; inserted only before `z` and at EOF.
        assertThat(render(toks)).isEqualTo("if ( c ) { } else if ( d ) { } else { } ~;~ z ~;~ <EOF>");
    }

    /** Leading-dot chains continue across newlines (member-chaining rule). */
    @Test
    public void leadingDotSuppressesInsertionForMemberChains() {
        String src = "val result = service\n    .load()\n    .transform()";
        List<Token> toks = delivered(src);
        assertThat(semis(toks, true).size()).as("exactly one terminator for the single statement").isEqualTo(1);
        assertThat(offsets(semis(toks, true))).isEqualTo(List.of(src.length()));
        assertThat(render(toks)).isEqualTo("val result = service . load ( ) . transform ( ) ~;~ <EOF>");
    }

    /**
     * `?.` participates in insertion today even though nullable member access becomes parser
     * syntax only in Phase 10: the lookahead table treats it as a line continuation, so the chain
     * stays one statement at this layer.
     */
    @Test
    public void nullableDotSuppressesInsertionAtTheTokenStreamLayer() {
        String src = "val result = service\n    ?.load()\n    ?.transform()";
        List<Token> toks = delivered(src);
        // If insertion fired before a `?.`, the chain would split into several statements.
        assertThat(semis(toks, true).size()).isEqualTo(1);
        assertThat(offsets(semis(toks, true))).isEqualTo(List.of(src.length()));
        assertThat(render(toks)).isEqualTo("val result = service ?. load ( ) ?. transform ( ) ~;~ <EOF>");
        assertThat(SemicolonInsertingTokenSource.isMemberChainContinuation(SolvikLexer.NULLABLE_DOT)).isTrue();
    }

    /** A lone `?` is the nullable-type marker token and never the `?.` continuation token. */
    @Test
    public void loneQuestionMarkIsTheNullableMarker() {
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString("?"));
        lexer.removeErrorListeners();
        List<? extends Token> raw = lexer.getAllTokens();
        assertThat(raw.size()).isEqualTo(1);
        assertThat(raw.get(0).getType()).isEqualTo(SolvikLexer.QUESTION);
        assertThat(raw.stream().anyMatch(t -> t.getType() == SolvikLexer.NULLABLE_DOT)).isFalse();
    }

    /** The `?.` pair is one token, distinct from the lone `?` nullable-type marker. */
    @Test
    public void nullableDotIsOneTokenDistinctFromTheNullableMarker() {
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString("a?.b"));
        lexer.removeErrorListeners();
        List<? extends Token> raw = lexer.getAllTokens();
        assertThat(raw.stream().anyMatch(t -> t.getType() == SolvikLexer.NULLABLE_DOT)).isTrue();
        assertThat(raw.stream().anyMatch(t -> t.getType() == SolvikLexer.QUESTION)).isFalse();
    }

    /** A genuinely invalid character still produces a lexical error; `?` is no longer one. */
    @Test
    public void invalidCharacterStillProducesALexicalError() {
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString("@"));
        lexer.removeErrorListeners();
        AtomicInteger errors = new AtomicInteger();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                errors.incrementAndGet();
            }
        });
        lexer.getAllTokens();
        assertThat(errors.get() > 0).as("`@` must produce a lexical error").isTrue();
    }

    /** A newline contained in a block-comment body is a physical newline for insertion. */
    @Test
    public void newlineInsideBlockCommentActsAsPhysicalNewline() {
        String src = "val x = 1 /* first\nsecond */ val y = 2";
        List<Token> toks = delivered(src);
        // One semi inside the comment gap (after `1`) plus the EOF terminator after `2`.
        assertThat(offsets(semis(toks, true))).isEqualTo(List.of(src.indexOf('1') + 1, src.length()));
        assertThat(semis(toks, true).size() + semis(toks, false).size()).as("exactly two semis total, both synthesized").isEqualTo(2);
    }

    /** Line comments hide nothing relevant: the real newline behind them carries the boundary. */
    @Test
    public void lineCommentAfterStatementDoesNotDuplicateTheBoundary() {
        String src = "val x = 1 // trailing note\nval y = 2 // another\n";
        List<Token> toks = delivered(src);
        assertThat(semis(toks, true).size()).isEqualTo(2);
        assertThat(offsets(semis(toks, true))).isEqualTo(List.of(src.indexOf('1') + 1, src.indexOf('2') + 1));
    }

    /** The lookahead skips comments: a comment line cannot defeat the `.`/`else` exceptions. */
    @Test
    public void commentBetweenBoundaryAndNextTokenDoesNotDefeatTheLookahead() {
        String chain = "val x = service\n// intermediate note\n    .load()";
        assertThat(semis(delivered(chain), true).size()).isEqualTo(1);
        String danglingElse = "if (c) {\n} /* dangle\nover lines */ else {\n}\nz";
        assertThat(render(delivered(danglingElse))).as("no semi may land directly before `else`").isEqualTo("if ( c ) { } else { } ~;~ z ~;~ <EOF>");
    }

    /** Carriage-return styles are line terminators, matching SourceFile's physical-line model. */
    @Test
    public void crlfAndLoneCarriageReturnTerminateStatements() {
        assertThat(render(delivered("val x = 1\r\nval y = 2\r\n"))).isEqualTo("val x = 1 ~;~ val y = 2 ~;~ <EOF>");
        assertThat(render(delivered("val x = 1\rval y = 2\r"))).isEqualTo("val x = 1 ~;~ val y = 2 ~;~ <EOF>");
        assertThat(render(delivered("val x = 1\r\nval y = 2\r"))).isEqualTo("val x = 1 ~;~ val y = 2 ~;~ <EOF>");
    }

    /** Insertion never consults the parser: streams are rewritten identically on bad programs. */
    @Test
    public void insertionProceedsOnProgramsTheParserWillReject() {
        // Dangling `=` before EOF: the lexical rules still apply deterministically.
        assertThat(render(delivered("val x = 1\nval y ="))).isEqualTo("val x = 1 ~;~ val y = <EOF>");
        // An invalid character does not disturb the surrounding insertions and never gets a semi.
        String rendered = render(delivered("val x = 1\n@\nval y = 2"));
        assertThat(rendered.startsWith("val x = 1 ~;~")).as(rendered).isTrue();
        assertThat(rendered.contains("~;~ @")).as(rendered).isFalse();
    }

    /** The newline-terminator table pins the specification list exactly. */
    @Test
    public void terminatorTablePinsTheSpecificationList() {
        List<Integer> terminators = List.of( //
                SolvikLexer.Identifier, SolvikLexer.INT_LITERAL, SolvikLexer.LONG_LITERAL, //
                SolvikLexer.FLOATING_LITERAL, SolvikLexer.STRING_LITERAL, //
                SolvikLexer.RAW_STRING_LITERAL, SolvikLexer.CHAR_LITERAL, SolvikLexer.BOOL_LITERAL, //
                SolvikLexer.NULL, SolvikLexer.QUESTION, SolvikLexer.THIS, SolvikLexer.BREAK, //
                SolvikLexer.CONTINUE, SolvikLexer.RETURN, SolvikLexer.RPAREN, SolvikLexer.RBRACKET, //
                SolvikLexer.RBRACE);
        List<Integer> nonTerminators = List.of( //
                SolvikLexer.FUNC, SolvikLexer.CLASS, SolvikLexer.INTERFACE, SolvikLexer.IMPLEMENTS, //
                SolvikLexer.OPEN, SolvikLexer.EXTENDS, SolvikLexer.DELEGATE, SolvikLexer.ENUM, //
                SolvikLexer.SEALED, SolvikLexer.MATCH, SolvikLexer.ARROW, //
                SolvikLexer.SWITCH, SolvikLexer.CASE, SolvikLexer.DEFAULT, SolvikLexer.REGEX_KW, //
                SolvikLexer.OVERRIDE, SolvikLexer.SUPER, SolvikLexer.VAL, //
                SolvikLexer.VAR, SolvikLexer.IF, SolvikLexer.ELSE, //
                SolvikLexer.WHILE, SolvikLexer.FOR, SolvikLexer.LPAREN, SolvikLexer.LBRACKET, //
                SolvikLexer.LBRACE, SolvikLexer.SEMI, SolvikLexer.ASSIGN, SolvikLexer.COLON, //
                SolvikLexer.COMMA, SolvikLexer.DOT, SolvikLexer.NULLABLE_DOT, SolvikLexer.ADD, //
                SolvikLexer.SUB, SolvikLexer.MUL, SolvikLexer.DIV, SolvikLexer.BANG, SolvikLexer.EQ, //
                SolvikLexer.NEQ, SolvikLexer.LT, SolvikLexer.LE, SolvikLexer.GT, SolvikLexer.GE, //
                SolvikLexer.AND, SolvikLexer.OR, SolvikLexer.IS, SolvikLexer.AS, //
                SolvikLexer.NULL_COALESCE, Token.EOF);
        for (int type : terminators) {
            assertThat(SemicolonInsertingTokenSource.isNewlineTerminator(type)).as(typeLabel(type)).isTrue();
        }
        for (int type : nonTerminators) {
            assertThat(SemicolonInsertingTokenSource.isNewlineTerminator(type)).as(typeLabel(type)).isFalse();
        }
    }

    /** The continuation table is exactly `.`, `?.`, and `else`: no general JS heuristics. */
    @Test
    public void continuationTableIsExactlyTheSpecifiedExceptions() {
        for (int type : List.of(SolvikLexer.DOT, SolvikLexer.NULLABLE_DOT, SolvikLexer.ELSE)) {
            assertThat(SemicolonInsertingTokenSource.isMemberChainContinuation(type)).as(typeLabel(type)).isTrue();
        }
        for (int type : List.of(SolvikLexer.ADD, SolvikLexer.SUB, SolvikLexer.MUL, SolvikLexer.DIV, //
                SolvikLexer.COMMA, SolvikLexer.ASSIGN, SolvikLexer.COLON, SolvikLexer.Identifier, //
                SolvikLexer.INT_LITERAL, Token.EOF)) {
            assertThat(SemicolonInsertingTokenSource.isMemberChainContinuation(type)).as(typeLabel(type)).isFalse();
        }
    }

    /** Two independent runs over the same source must insert identical tokens at identical offsets. */
    @Test
    public void insertionIsDeterministicAcrossRuns() {
        String src = "func f(): Int {\n    val x = 1\n    val y = x + 2\n    return y\n}\n";
        assertThat(render(delivered(src))).isEqualTo(render(delivered(src)));
        assertThat(offsets(semis(delivered(src), true))).isEqualTo(offsets(semis(delivered(src), true)));
    }

    private static String typeLabel(int type) {
        return type == Token.EOF ? "EOF" : SolvikLexer.VOCABULARY.getDisplayName(type);
    }
}
