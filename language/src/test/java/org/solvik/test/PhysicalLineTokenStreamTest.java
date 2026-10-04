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
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import org.solvik.parser.PhysicalLineTokenSource;
import org.solvik.parser.generated.SolvikLexer;

/**
 * Pins the line-boundary token stream of {@code docs/LANGUAGE_SPEC.md} section 16 to the specification
 * by driving {@link PhysicalLineTokenSource} directly, without running the parser: which physical lines
 * end, and which do not.
 *
 * <p>The grammar decides which line breaks may continue a construct by absorbing the {@code NEWLINE}
 * tokens this stage places, so these tests assert placement alone. A break the specification makes a
 * continuation still appears here; the tests below pin the complementary half - that a line the
 * specification ends really does carry a boundary, and that a line the specification does not end never
 * carries a spurious one.
 */
public final class PhysicalLineTokenStreamTest {

    /** The tokens the parser would see: this stage's output on the default channel. */
    private static List<Token> delivered(final String source) {
        final SolvikLexer lexer = new SolvikLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        final PhysicalLineTokenSource stream = new PhysicalLineTokenSource(lexer);
        final List<Token> out = new ArrayList<>();
        while (true) {
            final Token token = stream.nextToken();
            if (token.getChannel() == Token.DEFAULT_CHANNEL) {
                out.add(token);
            }
            if (token.getType() == Token.EOF) {
                // A stream asked past exhaustion keeps answering EOF and never adds a boundary again.
                assertThat(stream.nextToken().getType()).isEqualTo(Token.EOF);
                return out;
            }
        }
    }

    /** The count of line boundaries in one source. */
    private static long boundaries(final String source) {
        return delivered(source).stream().filter(PhysicalLineTokenSource::isLineBoundary).count();
    }

    @Test
    public void aLineEndingInAValueCarriesOneBoundary() {
        assertThat(boundaries("val x = 1\n")).isEqualTo(1);
        assertThat(boundaries("val x = 1\nval y = 2\n")).isEqualTo(2);
    }

    @Test
    public void endOfFileTerminatesTheLastLineOnce() {
        assertThat(boundaries("val x = 1")).isEqualTo(1);
        assertThat(delivered("val x = 1").get(delivered("val x = 1").size() - 1).getType()).isNotEqualTo(SolvikLexer.NEWLINE);
    }

    @Test
    public void aBodyAndItsClosingBraceEachEndTheirLine() {
        String source = "func f(): Unit {\n    println(1)\n}\n";
        List<Token> tokens = delivered(source);
        List<Integer> breaks = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (PhysicalLineTokenSource.isLineBoundary(tokens.get(i))) {
                breaks.add(i);
            }
        }
        // After the call's `)`, after the block's `}`, and nothing else: the header's `)` before the
        // body's `{` is not a line end, because the body belongs to the line that opened it.
        assertThat(breaks).hasSize(2);
        assertThat(tokens.get(breaks.get(0) - 1).getType()).isEqualTo(SolvikLexer.RPAREN);
        assertThat(tokens.get(breaks.get(1) - 1).getType()).isEqualTo(SolvikLexer.RBRACE);
    }

    @Test
    public void blankAndCommentLinesNeverAddASecondBoundary() {
        assertThat(boundaries("val a = 1\n\n\nval b = 2\n")).isEqualTo(2);
        assertThat(boundaries("val a = 1\n// a comment\nval b = 2\n")).isEqualTo(2);
    }

    @Test
    public void anExplicitSeparatorIsNotALineBoundary() {
        // One physical line, two statements: the `;` is the separator and only the line break ends it.
        assertThat(boundaries("print(a); print(b)\n")).isEqualTo(1);
    }

    @Test
    public void aContinuationLineCarriesABoundaryOnlyWhenItsLastTokenEndsALine() {
        // Placement consults only the token before the break. Inside the argument list the lines after
        // `(` and after `1,` carry none, because `(` and `,` cannot end a line; the line holding `2`
        // ends on its value and the line holding `)` ends on the closer, so the grammar absorbs the
        // break before the `)` from its own `NEWLINE*`.
        assertThat(boundaries("val xs = foo(\n    1,\n    2\n)\n")).isEqualTo(2);
        // The line broken after `=` carries no boundary - an assignment line cannot end there - and only
        // the line the value closes ends.
        assertThat(boundaries("val x =\n    1\n")).isEqualTo(1);
    }

    @Test
    public void theTerminatorTableIsTheSpecificationList() {
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.Identifier)).isTrue();
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.INTEGER_LITERAL)).isTrue();
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.STRING_LITERAL)).isTrue();
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.THIS)).isTrue();
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.BREAK)).isTrue();
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.RETURN)).isTrue();
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.RPAREN)).isTrue();
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.RBRACE)).isTrue();

        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.VAL)).isFalse();
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.MUTABLE)).isFalse();
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.IF)).isFalse();
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.ELSE)).isFalse();
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.TRY)).isFalse();
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.ASSIGN)).isFalse();
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.COMMA)).isFalse();
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.DOT)).isFalse();
        // `;` ends its line: a program that closes a line with one gets the single boundary that
        // follows it, while a `;` between two constructs of one line stays a plain separator.
        assertThat(PhysicalLineTokenSource.endsLine(SolvikLexer.SEMI)).isTrue();
    }

    @Test
    public void anExplicitSeparatorClosingALineCarriesOneBoundary() {
        assertThat(boundaries("val a = 1\n")).isEqualTo(1);
        assertThat(boundaries("val a = 1\nval b = 2\n")).isEqualTo(2);
        // Two constructs on one line separated by `;` and one line break: one boundary, one `;`.
        List<Token> tokens = delivered("val a = 1; val b = 2\n");
        assertThat(tokens.stream().filter(t -> t.getType() == SolvikLexer.SEMI).count()).isEqualTo(1);
        assertThat(boundaries("val a = 1; val b = 2\n")).isEqualTo(1);
    }

    @Test
    public void boundariesArePlacedInsideBracketsSoABodySeparatesItsStatements() {
        // Nesting depth is not consulted: the statements of a bracketed anonymous function terminate
        // exactly like statements anywhere else, which is what lets a body sit inside a call at all.
        String source = "foo(\n    func(x: Integer): Integer {\n        val y = x\n        return y\n    }\n)\n";
        assertThat(boundaries(source)).isEqualTo(4);
    }

    @Test
    public void aBoundaryIsIdentifiableAsPlacedByThisStage() {
        List<Token> tokens = delivered("val x = 1\n");
        Token boundary = tokens.stream().filter(t -> t.getType() == SolvikLexer.NEWLINE).findFirst().orElseThrow();
        assertThat(PhysicalLineTokenSource.isLineBoundary(boundary)).isTrue();
        assertThat(boundary.getChannel()).isEqualTo(Token.DEFAULT_CHANNEL);
        assertThat(boundary.getLine()).isEqualTo(1);
    }
}
