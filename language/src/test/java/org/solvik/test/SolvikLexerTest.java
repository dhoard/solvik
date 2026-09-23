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
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import org.solvik.parser.generated.SolvikLexer;

/**
 * Direct lexer-layer tests for {@code docs/LANGUAGE_SPEC.md} section 1 (lexical basics) and the
 * raw-string rules of section 15. Each case drives {@link SolvikLexer} directly (without the
 * semicolon-inserting stage or the parser) and pins the default-channel token types, spellings, and
 * source spans, plus the negative cases where the lexer must reject a malformed token. Comment and
 * numeric forms are exercised because they are the most error-prone longest-match rules.
 */
public final class SolvikLexerTest {

    /** A recorded lexer observation: either a delivered token or a reported lexical error. */
    private record Lexed(String name, String text, int line, int col, int start, int stop) {
    }

    /** Runs the lexer over {@code src}, returning default-channel tokens (hidden dropped) plus any
     * lexical-error messages, in the order the lexer produced them. */
    private static List<String> errorMessages(String src) {
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString(src));
        List<String> errors = new ArrayList<>();
        lexer.removeErrorListeners();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                errors.add("line " + line + " col " + charPositionInLine + ": " + msg);
            }
        });
        Token t;
        while ((t = lexer.nextToken()).getType() != Token.EOF) {
            // Drain all tokens so the lexer reaches end of input and reports EOF-terminated errors.
        }
        return errors;
    }

    /** Default-channel token rendering: symbolic name and exact spelling, joined by spaces. */
    private static String renderDefault(String src) {
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString(src));
        lexer.removeErrorListeners();
        StringBuilder sb = new StringBuilder();
        while (true) {
            Token t = lexer.nextToken();
            if (t.getType() == Token.EOF) {
                break;
            }
            if (t.getChannel() != Token.DEFAULT_CHANNEL) {
                continue;
            }
            sb.append(SolvikLexer.VOCABULARY.getSymbolicName(t.getType())).append('(').append(t.getText()).append(") ");
        }
        return sb.toString().trim();
    }

    /** The first default-channel token's span record. */
    private static Lexed firstDefault(String src) {
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString(src));
        lexer.removeErrorListeners();
        while (true) {
            Token t = lexer.nextToken();
            if (t.getType() == Token.EOF) {
                return null;
            }
            if (t.getChannel() == Token.DEFAULT_CHANNEL) {
                return new Lexed(SolvikLexer.VOCABULARY.getSymbolicName(t.getType()), t.getText(), t.getLine(), t.getCharPositionInLine(), t.getStartIndex(), t.getStopIndex());
            }
        }
    }

    // ---- Identifiers (section 1: [A-Za-z_][A-Za-z0-9_]*) -------------------------------------

    @Test
    public void identifiersAcceptLettersDigitsAndUnderscores() {
        assertThat(renderDefault("_x __y a1 B2 z_9")).contains("Identifier(_x)").contains("Identifier(__y)").contains("Identifier(a1)").contains("Identifier(B2)").contains("Identifier(z_9)");
    }

    @Test
    public void underscoreIsItsOwnValidIdentifier() {
        Lexed t = firstDefault("_");
        assertThat(t.name()).isEqualTo("Identifier");
        assertThat(t.text()).isEqualTo("_");
    }

    @Test
    public void dollarIsNotAnIdentifierCharacter() {
        // '$' has no token production; the lexer must report an unrecognized character.
        assertThat(errorMessages("a$b")).isNotEmpty();
    }

    @Test
    public void digitLeadSplitsIntoIntegerThenIdentifier() {
        // '1x' cannot be one identifier: the lexer emits INTEGER then Identifier as two tokens.
        assertThat(renderDefault("1x")).isEqualTo("INTEGER_LITERAL(1) Identifier(x)");
    }

    @Test
    public void keywordsAndIdentifiersShareTheLongestMatch() {
        // 'in' is a keyword but 'index' is a single identifier, never 'in' + 'dex'.
        assertThat(renderDefault("index")).isEqualTo("Identifier(index)");
        assertThat(renderDefault("in")).isEqualTo("IN(in)");
    }

    // ---- Integer / Long literals (section 1, Phase 7) ----------------------------------------

    @Test
    public void decimalIntegerLiteral() {
        Lexed t = firstDefault("0");
        assertThat(t.name()).isEqualTo("INTEGER_LITERAL");
        assertThat(t.text()).isEqualTo("0");
    }

    @Test
    public void longSuffixBothCasesLexAsOneLongToken() {
        assertThat(renderDefault("123L")).isEqualTo("LONG_LITERAL(123L)");
        assertThat(renderDefault("123l")).isEqualTo("LONG_LITERAL(123l)");
    }

    // ---- Floating-point literals (section 1, Phase 7) ----------------------------------------

    @Test
    public void floatingFormsLexAsOneToken() {
        assertThat(renderDefault("1.5")).isEqualTo("FLOATING_LITERAL(1.5)");
        assertThat(renderDefault("1e3")).isEqualTo("FLOATING_LITERAL(1e3)");
        assertThat(renderDefault("1e+3")).isEqualTo("FLOATING_LITERAL(1e+3)");
        assertThat(renderDefault("1e-3")).isEqualTo("FLOATING_LITERAL(1e-3)");
        assertThat(renderDefault("1.5e-3")).isEqualTo("FLOATING_LITERAL(1.5e-3)");
        assertThat(renderDefault("2.5f")).isEqualTo("FLOATING_LITERAL(2.5f)");
        assertThat(renderDefault("2.5F")).isEqualTo("FLOATING_LITERAL(2.5F)");
        assertThat(renderDefault("1e3f")).isEqualTo("FLOATING_LITERAL(1e3f)");
    }

    @Test
    public void incompleteExponentIsSplitOffAsAnIdentifier() {
        // '2e' has no valid float form ('e' needs a following digit); lexer yields INTEGER then 'e'.
        assertThat(renderDefault("2e")).isEqualTo("INTEGER_LITERAL(2) Identifier(e)");
    }

    @Test
    public void trailingDotIsConcatNotAFloatingLiteral() {
        // '1.' cannot be a float (no fractional digits); it lexes as INTEGER then DOTDOT-prefixed.
        assertThat(renderDefault("1.")).isEqualTo("INTEGER_LITERAL(1) DOT(.)");
    }

    // ---- String literals (section 15) --------------------------------------------------------

    @Test
    public void normalStringIsOneTokenIncludingEscapes() {
        Lexed t = firstDefault("\"a\\nb\"");
        assertThat(t.name()).isEqualTo("STRING_LITERAL");
        assertThat(t.text()).isEqualTo("\"a\\nb\"");
    }

    @Test
    public void normalStringRejectsRawNewline() {
        // A physical newline inside a normal string is not lexable as one token.
        assertThat(errorMessages("\"a\nb\"")).isNotEmpty();
    }

    @Test
    public void unterminatedStringIsReported() {
        assertThat(errorMessages("\"abc")).isNotEmpty();
    }

    // ---- Raw strings (section 15) ------------------------------------------------------------

    @Test
    public void rawStringZeroHashes() {
        Lexed t = firstDefault("r\"simple\"");
        assertThat(t.name()).isEqualTo("RAW_STRING_LITERAL");
        assertThat(t.text()).isEqualTo("r\"simple\"");
    }

    @Test
    public void rawStringOneHash() {
        Lexed t = firstDefault("r#\"Test '\"#");
        assertThat(t.name()).isEqualTo("RAW_STRING_LITERAL");
        assertThat(t.text()).isEqualTo("r#\"Test '\"#");
    }

    @Test
    public void rawStringTwoHashesPreservesInnerQuoteHash() {
        // The opening delimiter fixes N=2; the content may contain "# (a quote with the wrong hash
        // count) and only a quote followed by exactly two hashes closes it.
        Lexed t = firstDefault("r##\"contains \"# text\"##");
        assertThat(t.name()).isEqualTo("RAW_STRING_LITERAL");
        assertThat(t.text()).isEqualTo("r##\"contains \"# text\"##");
    }

    @Test
    public void rawStringThreeHashes() {
        assertThat(firstDefault("r###\"arbitrary\"###").text()).isEqualTo("r###\"arbitrary\"###");
    }

    @Test
    public void rawStringKeepsEmbeddedNewlinesInOneToken() {
        // A raw string spans physical newlines as a single token; the following token reports the
        // correct advanced line, proving the body did not leak newlines to the token stream.
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString("r#\"a\nb\"#\nval"));
        lexer.removeErrorListeners();
        Token raw = lexer.nextToken();
        assertThat(SolvikLexer.VOCABULARY.getSymbolicName(raw.getType())).isEqualTo("RAW_STRING_LITERAL");
        assertThat(raw.getText()).isEqualTo("r#\"a\nb\"#");
        // Advance past the hidden newline and read the next default token.
        Token next;
        do {
            next = lexer.nextToken();
        } while (next.getChannel() != Token.DEFAULT_CHANNEL && next.getType() != Token.EOF);
        assertThat(SolvikLexer.VOCABULARY.getSymbolicName(next.getType())).isEqualTo("VAL");
        assertThat(next.getLine()).isEqualTo(3);
    }

    @Test
    public void rawIsIdentifierWhenNotFollowedByHashQuote() {
        // 'r' not immediately followed by '#'* '"' is lexed as an ordinary identifier.
        assertThat(renderDefault("rawx")).isEqualTo("Identifier(rawx)");
        assertThat(renderDefault("r")).isEqualTo("Identifier(r)");
    }

    @Test
    public void unterminatedRawStringIsReported() {
        assertThat(errorMessages("r#\"abc")).isNotEmpty();
    }

    // ---- Comments (section 1: non-nesting block comment) ---------------------------------------

    @Test
    public void blockCommentIsNonNesting() {
        // '/* /* */' closes at the FIRST '*/': after that '*/' the trailing '*/' is unexpected but
        // the comment itself must have ended. We assert the comment token itself spans only to the
        // first '*/'.
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString("/* a /* b */ rest"));
        lexer.removeErrorListeners();
        Token c = lexer.nextToken();
        assertThat(SolvikLexer.VOCABULARY.getSymbolicName(c.getType())).isEqualTo("BLOCK_COMMENT");
        assertThat(c.getText()).isEqualTo("/* a /* b */");
    }

    @Test
    public void lineCommentAndBlockCommentAreHiddenTokens() {
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString("// line\n/* block */"));
        lexer.removeErrorListeners();
        boolean sawLine = false;
        boolean sawBlock = false;
        Token t;
        while ((t = lexer.nextToken()).getType() != Token.EOF) {
            if (SolvikLexer.VOCABULARY.getSymbolicName(t.getType()).equals("LINE_COMMENT")) {
                sawLine = true;
                assertThat(t.getChannel()).isNotEqualTo(Token.DEFAULT_CHANNEL);
            }
            if (SolvikLexer.VOCABULARY.getSymbolicName(t.getType()).equals("BLOCK_COMMENT")) {
                sawBlock = true;
                assertThat(t.getChannel()).isNotEqualTo(Token.DEFAULT_CHANNEL);
            }
        }
        assertThat(sawLine).isTrue();
        assertThat(sawBlock).isTrue();
    }

    // ---- Operator longest match --------------------------------------------------------------

    @Test
    public void equalityAndIdentityOperatorsAreDistinctTokens() {
        assertThat(renderDefault("==")).isEqualTo("EQ(==)");
        assertThat(renderDefault("===")).isEqualTo("EQEQ(===)");
        assertThat(renderDefault("!=")).isEqualTo("NEQ(!=)");
        assertThat(renderDefault("!==")).isEqualTo("NEQEQ(!==)");
    }

    @Test
    public void rangeOperatorsAreDistinctFromConcatAndDot() {
        assertThat(renderDefault("...")).isEqualTo("DOTDOTDOT(...)");
        assertThat(renderDefault("..<")).isEqualTo("DOTDOTLT(..<)");
        assertThat(renderDefault("..>")).isEqualTo("DOTDOTGT(..>)");
        assertThat(renderDefault("..")).isEqualTo("DOTDOT(..)");
        assertThat(renderDefault(".")).isEqualTo("DOT(.)");
    }

    @Test
    public void nullableDotAndCoalesceAreDistinctFromSingleQuestion() {
        assertThat(renderDefault("?.")).isEqualTo("NULLABLE_DOT(?.)");
        assertThat(renderDefault("??")).isEqualTo("NULL_COALESCE(??)");
        assertThat(renderDefault("?")).isEqualTo("QUESTION(?)");
    }

    @Test
    public void namespaceSeparatorIsOneToken() {
        assertThat(renderDefault("::")).isEqualTo("COLONCOLON(::)");
    }

    @Test
    public void logicalOperatorsAreDistinct() {
        assertThat(renderDefault("&&")).isEqualTo("AND(&&)");
        assertThat(renderDefault("||")).isEqualTo("OR(||)");
    }

    // ---- Additional operator coverage -------------------------------------------------------

    @Test
    public void orderingOperatorsAreDistinctFromEquality() {
        assertThat(renderDefault("<")).isEqualTo("LT(<)");
        assertThat(renderDefault("<=")).isEqualTo("LE(<=)");
        assertThat(renderDefault(">")).isEqualTo("GT(>)");
        assertThat(renderDefault(">=")).isEqualTo("GE(>=)");
    }

    @Test
    public void matchArrowIsOneToken() {
        assertThat(renderDefault("=>")).isEqualTo("ARROW(=>)");
    }

    @Test
    public void punctuationDelimitersAreDistinctTokens() {
        assertThat(renderDefault("(")).isEqualTo("LPAREN(()");
        assertThat(renderDefault(")")).isEqualTo("RPAREN())");
        assertThat(renderDefault("{")).isEqualTo("LBRACE({)");
        assertThat(renderDefault("}")).isEqualTo("RBRACE(})");
        assertThat(renderDefault("[")).isEqualTo("LBRACKET([)");
        assertThat(renderDefault("]")).isEqualTo("RBRACKET(])");
        assertThat(renderDefault(";")).isEqualTo("SEMI(;)");
        assertThat(renderDefault(",")).isEqualTo("COMMA(,)");
        assertThat(renderDefault("=")).isEqualTo("ASSIGN(=)");
        assertThat(renderDefault(":")).isEqualTo("COLON(:)");
    }

    @Test
    public void arithmeticOperatorsAreDistinctTokens() {
        assertThat(renderDefault("+")).isEqualTo("ADD(+)");
        assertThat(renderDefault("-")).isEqualTo("SUB(-)");
        assertThat(renderDefault("*")).isEqualTo("MUL(*)");
        assertThat(renderDefault("/")).isEqualTo("DIV(/)");
        assertThat(renderDefault("!")).isEqualTo("BANG(!)");
    }

    @Test
    public void dotIsSeparatedFromLongerDotSequencesByLongestMatch() {
        // Three adjacent dots are DOTDOTDOT, never DOT+DOTDOT; two dots are DOTDOT, never DOT+DOT.
        assertThat(renderDefault("...")).isEqualTo("DOTDOTDOT(...)");
        assertThat(renderDefault("..")).isEqualTo("DOTDOT(..)");
        assertThat(renderDefault(".")).isEqualTo("DOT(.)");
    }

    // ---- Numeric boundaries -----------------------------------------------------------------

    @Test
    public void leadingZerosRemainDecimalNotOctal() {
        // The spec fixes the core to decimal-only: '017' is a single decimal INTEGER_LITERAL.
        assertThat(renderDefault("017")).isEqualTo("INTEGER_LITERAL(017)");
        assertThat(renderDefault("0")).isEqualTo("INTEGER_LITERAL(0)");
    }

    @Test
    public void digitSeparatorsAreNotPartOfNumericLiterals() {
        // '1_000' is not a single numeric literal because Solvik defines only decimal digits.
        assertThat(renderDefault("1_000")).isEqualTo("INTEGER_LITERAL(1) Identifier(_000)");
    }

    @Test
    public void hexOctalAndBinaryFormsAreNotRecognized() {
        // Solvik's literal set is decimal only. '0xFF' is INTEGER_LITERAL(0) then Identifier(xFF).
        assertThat(renderDefault("0xFF")).isEqualTo("INTEGER_LITERAL(0) Identifier(xFF)");
        assertThat(renderDefault("0b1010")).isEqualTo("INTEGER_LITERAL(0) Identifier(b1010)");
    }

    @Test
    public void allFloatingAlternativesAreSingleTokens() {
        assertThat(renderDefault("1.5")).isEqualTo("FLOATING_LITERAL(1.5)");
        assertThat(renderDefault("1.5f")).isEqualTo("FLOATING_LITERAL(1.5f)");
        assertThat(renderDefault("1.5F")).isEqualTo("FLOATING_LITERAL(1.5F)");
        assertThat(renderDefault("1e3")).isEqualTo("FLOATING_LITERAL(1e3)");
        assertThat(renderDefault("1E3")).isEqualTo("FLOATING_LITERAL(1E3)");
        assertThat(renderDefault("1e+3")).isEqualTo("FLOATING_LITERAL(1e+3)");
        assertThat(renderDefault("1e-3")).isEqualTo("FLOATING_LITERAL(1e-3)");
        assertThat(renderDefault("1.5e+10f")).isEqualTo("FLOATING_LITERAL(1.5e+10f)");
        assertThat(renderDefault("1.5E-10F")).isEqualTo("FLOATING_LITERAL(1.5E-10F)");
    }

    @Test
    public void longSuffixAcceptsBothLetterCases() {
        assertThat(renderDefault("0L")).isEqualTo("LONG_LITERAL(0L)");
        assertThat(renderDefault("9999999999L")).isEqualTo("LONG_LITERAL(9999999999L)");
        assertThat(renderDefault("1234l")).isEqualTo("LONG_LITERAL(1234l)");
    }

    @Test
    public void dotAfterLongSuffixStillSplitsAsConcat() {
        // '1L..2L' lexes as LONG / DOTDOT / LONG because '..' is concat, not a float continuation.
        assertThat(renderDefault("1L..2L")).isEqualTo("LONG_LITERAL(1L) DOTDOT(..) LONG_LITERAL(2L)");
    }

    @Test
    public void floatFollowedByConcatIsSplitCorrectly() {
        // '1.0..x' must not consume '..' as part of a malformed float.
        assertThat(renderDefault("1.0..x")).isEqualTo("FLOATING_LITERAL(1.0) DOTDOT(..) Identifier(x)");
    }

    @Test
    public void leadingDotIsConcatOrDotNeverAFloat() {
        // '.5' is DOT then INTEGER_LITERAL: the grammar has no leading-dot floating alternative.
        assertThat(renderDefault(".5")).isEqualTo("DOT(.) INTEGER_LITERAL(5)");
    }

    // ---- Character literals (lexer-level only; escape validity is a separate layer) ----------

    @Test
    public void singleAsciiCharacterIsOneToken() {
        Lexed t = firstDefault("'A'");
        assertThat(t.name()).isEqualTo("CHARACTER_LITERAL");
        assertThat(t.text()).isEqualTo("'A'");
    }

    @Test
    public void characterEscapesAreOneTokenAtTheLexerLevel() {
        // The lexer admits any single backslash-escape body; the semantic layer filters unsupported
        // escapes so the lexer only has to keep the literal contiguous.
        assertThat(renderDefault("'\\n'")).isEqualTo("CHARACTER_LITERAL('\\n')");
        assertThat(renderDefault("'\\\\'")).isEqualTo("CHARACTER_LITERAL('\\\\')");
        assertThat(renderDefault("'\\''")).isEqualTo("CHARACTER_LITERAL('\\'')");
    }

    @Test
    public void emptyCharacterLiteralIsALexerError() {
        assertThat(errorMessages("''")).isNotEmpty();
    }

    @Test
    public void multiCharacterLiteralIsALexerError() {
        assertThat(errorMessages("'AB'")).isNotEmpty();
    }

    @Test
    public void characterLiteralWithBareNewlineIsALexerError() {
        assertThat(errorMessages("'\n'")).isNotEmpty();
    }

    // ---- Comment / whitespace interaction ----------------------------------------------------

    @Test
    public void lineCommentAfterCodeDoesNotLeakIntoTokens() {
        // A line comment consumes to end of line; the next token must be the code after the newline.
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString("val x = 1 // trailing\nval y = 2"));
        lexer.removeErrorListeners();
        List<String> defaults = new ArrayList<>();
        Token t;
        while ((t = lexer.nextToken()).getType() != Token.EOF) {
            if (t.getChannel() == Token.DEFAULT_CHANNEL) {
                defaults.add(SolvikLexer.VOCABULARY.getSymbolicName(t.getType()));
            }
        }
        assertThat(defaults).containsExactly("VAL", "Identifier", "ASSIGN", "INTEGER_LITERAL", "VAL", "Identifier", "ASSIGN", "INTEGER_LITERAL");
    }

    @Test
    public void lineCommentContentIsNotReInterpretedAsCode() {
        // '/*' inside a line comment must not begin a block comment: the line comment consumes it.
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString("// see /* here\nval"));
        lexer.removeErrorListeners();
        Token t;
        List<String> seen = new ArrayList<>();
        while ((t = lexer.nextToken()).getType() != Token.EOF) {
            if (t.getChannel() == Token.DEFAULT_CHANNEL) {
                seen.add(SolvikLexer.VOCABULARY.getSymbolicName(t.getType()));
            } else if (SolvikLexer.VOCABULARY.getSymbolicName(t.getType()).equals("BLOCK_COMMENT")) {
                throw new AssertionError("block comment must not start inside a line comment");
            }
        }
        assertThat(seen).containsExactly("VAL");
    }

    @Test
    public void blockCommentContentIsNotReInterpretedAsTokens() {
        // A string-looking body inside a block comment must not become a STRING_LITERAL token.
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString("/* \"hi\" */val"));
        lexer.removeErrorListeners();
        Token t;
        List<String> seen = new ArrayList<>();
        while ((t = lexer.nextToken()).getType() != Token.EOF) {
            if (t.getChannel() == Token.DEFAULT_CHANNEL) {
                seen.add(SolvikLexer.VOCABULARY.getSymbolicName(t.getType()));
            } else if (SolvikLexer.VOCABULARY.getSymbolicName(t.getType()).equals("STRING_LITERAL")) {
                throw new AssertionError("string rule must not fire inside a block comment");
            }
        }
        assertThat(seen).containsExactly("VAL");
    }

    @Test
    public void newlineInsideBlockCommentPreservesLineTracking() {
        // A block comment that spans physical newlines still advances the lexer's line counter so
        // the next real token carries the correct absolute line.
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString("val\n/* a\nb */\nx"));
        lexer.removeErrorListeners();
        Token x = null;
        Token t;
        while ((t = lexer.nextToken()).getType() != Token.EOF) {
            if (t.getChannel() == Token.DEFAULT_CHANNEL) {
                x = t;
            }
        }
        assertThat(x).isNotNull();
        assertThat(SolvikLexer.VOCABULARY.getSymbolicName(x.getType())).isEqualTo("Identifier");
        assertThat(x.getLine()).isEqualTo(4);
        assertThat(x.getCharPositionInLine()).isEqualTo(0);
    }

    @Test
    public void carriageReturnLineFeedIsAFirstClassLineTerminator() {
        // Section 16 treats CRLF, CR, and LF as physical newlines: after a CRLF, line is 2.
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString("val\r\nx"));
        lexer.removeErrorListeners();
        Token x = null;
        Token t;
        while ((t = lexer.nextToken()).getType() != Token.EOF) {
            if (t.getChannel() == Token.DEFAULT_CHANNEL) {
                x = t;
            }
        }
        assertThat(SolvikLexer.VOCABULARY.getSymbolicName(x.getType())).isEqualTo("Identifier");
        assertThat(x.getLine()).isEqualTo(2);
        assertThat(x.getCharPositionInLine()).isEqualTo(0);
    }

    // ---- Raw-string hash boundary cases -------------------------------------------------------

    @Test
    public void rawStringWithNOneRejectsZeroHashClose() {
        // With N=1, a bare '"' does not close: the string continues past a bare quote.
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString("r\"a\"#\"#\nval"));
        lexer.removeErrorListeners();
        Token first = lexer.nextToken();
        assertThat(SolvikLexer.VOCABULARY.getSymbolicName(first.getType())).isEqualTo("RAW_STRING_LITERAL");
    }

    @Test
    public void rawStringWithNTwoAcceptsInnerNOneQuoteHash() {
        // N=2 admits the sequence '#': a quote with the wrong hash count is content.
        Lexed t = firstDefault("r##\"inner \"# tail\"##");
        assertThat(t.text()).isEqualTo("r##\"inner \"# tail\"##");
    }

    @Test
    public void rawStringWithNThreeClosesOnThreeHashesOnly() {
        Lexed t = firstDefault("r###\"x\"###");
        assertThat(t.text()).isEqualTo("r###\"x\"###");
    }

    @Test
    public void rawIsIdentifierWhenNotFollowedByHashOrQuote() {
        assertThat(renderDefault("r")).isEqualTo("Identifier(r)");
        assertThat(renderDefault("r_1")).isEqualTo("Identifier(r_1)");
        assertThat(renderDefault("rand")).isEqualTo("Identifier(rand)");
    }

    @Test
    public void rawStringUnterminatedProducesDiagnosticNamingClosingDelimiter() {
        // Section 15: the diagnostic must show the exact expected closing delimiter.
        List<String> errors = errorMessages("r##\"abc");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("unterminated raw string").contains("\"##");
    }

    // ---- Keyword and identifier longest-match -------------------------------------------------

    @Test
    public void keywordsAreIdentifiersWhenExtendedByMoreIdentifierChars() {
        // ANTLR's longest-match rule makes any keyword prefix become a single identifier.
        assertThat(renderDefault("iffy")).isEqualTo("Identifier(iffy)");
        assertThat(renderDefault("classy")).isEqualTo("Identifier(classy)");
        assertThat(renderDefault("returning")).isEqualTo("Identifier(returning)");
        assertThat(renderDefault("nullish")).isEqualTo("Identifier(nullish)");
        assertThat(renderDefault("inn")).isEqualTo("Identifier(inn)");
        assertThat(renderDefault("assumption")).isEqualTo("Identifier(assumption)");
        assertThat(renderDefault("ismatch")).isEqualTo("Identifier(ismatch)");
        assertThat(renderDefault("delegateval")).isEqualTo("Identifier(delegateval)");
    }

    @Test
    public void everyKeywordIsReservedAsASingleToken() {
        assertThat(renderDefault("func")).isEqualTo("FUNC(func)");
        assertThat(renderDefault("class")).isEqualTo("CLASS(class)");
        assertThat(renderDefault("interface")).isEqualTo("INTERFACE(interface)");
        assertThat(renderDefault("enum")).isEqualTo("ENUM(enum)");
        assertThat(renderDefault("sealed")).isEqualTo("SEALED(sealed)");
        assertThat(renderDefault("delegate")).isEqualTo("DELEGATE(delegate)");
        assertThat(renderDefault("implements")).isEqualTo("IMPLEMENTS(implements)");
        assertThat(renderDefault("open")).isEqualTo("OPEN(open)");
        assertThat(renderDefault("extends")).isEqualTo("EXTENDS(extends)");
        assertThat(renderDefault("override")).isEqualTo("OVERRIDE(override)");
        assertThat(renderDefault("this")).isEqualTo("THIS(this)");
        assertThat(renderDefault("super")).isEqualTo("SUPER(super)");
        assertThat(renderDefault("val")).isEqualTo("VAL(val)");
        assertThat(renderDefault("var")).isEqualTo("VAR(var)");
        assertThat(renderDefault("if")).isEqualTo("IF(if)");
        assertThat(renderDefault("else")).isEqualTo("ELSE(else)");
        assertThat(renderDefault("while")).isEqualTo("WHILE(while)");
        assertThat(renderDefault("for")).isEqualTo("FOR(for)");
        assertThat(renderDefault("in")).isEqualTo("IN(in)");
        assertThat(renderDefault("break")).isEqualTo("BREAK(break)");
        assertThat(renderDefault("continue")).isEqualTo("CONTINUE(continue)");
        assertThat(renderDefault("return")).isEqualTo("RETURN(return)");
        assertThat(renderDefault("match")).isEqualTo("MATCH(match)");
        assertThat(renderDefault("switch")).isEqualTo("SWITCH(switch)");
        assertThat(renderDefault("case")).isEqualTo("CASE(case)");
        assertThat(renderDefault("default")).isEqualTo("DEFAULT(default)");
        assertThat(renderDefault("regex")).isEqualTo("REGEX_KW(regex)");
        assertThat(renderDefault("null")).isEqualTo("NULL(null)");
        assertThat(renderDefault("is")).isEqualTo("IS(is)");
        assertThat(renderDefault("as")).isEqualTo("AS(as)");
        assertThat(renderDefault("include")).isEqualTo("INCLUDE(include)");
        assertThat(renderDefault("module")).isEqualTo("MODULE(module)");
        assertThat(renderDefault("alias")).isEqualTo("ALIAS(alias)");
    }

    @Test
    public void booleanKeywordsLexAsOneBooleanLiteralToken() {
        assertThat(renderDefault("true")).isEqualTo("BOOL_LITERAL(true)");
        assertThat(renderDefault("false")).isEqualTo("BOOL_LITERAL(false)");
    }

    // ---- Negative cases -----------------------------------------------------------------------

    @Test
    public void unrecognizedPunctuationProducesLexerErrors() {
        // Characters outside the Solvik token alphabet must never be silently skipped.
        assertThat(errorMessages("@")).isNotEmpty();
        assertThat(errorMessages("~")).isNotEmpty();
        assertThat(errorMessages("%")).isNotEmpty();
        assertThat(errorMessages("#")).isNotEmpty();
        assertThat(errorMessages("$")).isNotEmpty();
    }

    @Test
    public void loneBackslashProducesLexerError() {
        // A backslash outside a string or character literal is not a Solvik token.
        assertThat(errorMessages("a \\ b")).isNotEmpty();
    }

    @Test
    public void nestedBlockCommentClosesAtFirstCloseDelimiter() {
        // Section 1 mandates a non-nesting block comment: the FIRST '*/' ends the comment.
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString("/* /* inner */ outer */"));
        lexer.removeErrorListeners();
        Token first = lexer.nextToken();
        assertThat(SolvikLexer.VOCABULARY.getSymbolicName(first.getType())).isEqualTo("BLOCK_COMMENT");
        assertThat(first.getText()).isEqualTo("/* /* inner */");
    }
}
