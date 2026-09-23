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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import org.solvik.parser.generated.SolvikLexer;

/**
 * Exact token-stream tests for {@code docs/LANGUAGE_SPEC.md} section 1 (lexical basics) and section
 * 15 (strings). Where {@link SolvikLexerTest} asserts token names and spellings, this suite pins the
 * complete delivery record of every token: symbolic name, lexeme, inclusive start/stop offsets,
 * line, column, and channel, plus the exact failing input offset of every lexical error.
 *
 * <p>Assertions were produced by driving {@link SolvikLexer} directly and recording the delivered
 * stream; they are the reference the parser and the semicolon-inserting stage are built on, so an
 * off-by-one in a span or a mislabelled channel fails here rather than in a later phase.
 */
public final class SolvikLexerTokenStreamTest {

    /** One delivered token, rendered identically for every case. */
    private record Delivered(String name, String lexeme, int start, int stop, int line, int column, boolean defaultChannel) {
    }

    /** One lexical error callback, rendered identically for every case. */
    private record LexError(int line, int column, String message, int failingOffset) {
    }

    /** The delivered token list plus the collected error callbacks for one input. */
    private record Lexed(List<Delivered> tokens, List<LexError> errors, int eofLine, int eofColumn, int eofOffset) {
    }

    private static Lexed lex(String src) {
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString(src));
        lexer.removeErrorListeners();
        List<LexError> errors = new ArrayList<>();
        lexer.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {
                // Every "token recognition error" is a LexerNoViableAltException, which carries the
                // exact failing input offset; the raw-string helper reports no such offset.
                int failing = e instanceof LexerNoViableAltException noViableAlt ? noViableAlt.getStartIndex() : -1;
                errors.add(new LexError(line, charPositionInLine, msg, failing));
            }
        });
        List<Delivered> tokens = new ArrayList<>();
        Token t;
        while ((t = lexer.nextToken()).getType() != Token.EOF) {
            tokens.add(new Delivered(SolvikLexer.VOCABULARY.getSymbolicName(t.getType()), t.getText(), t.getStartIndex(), t.getStopIndex(), t.getLine(), t.getCharPositionInLine(), t.getChannel() == Token.DEFAULT_CHANNEL));
        }
        return new Lexed(tokens, errors, t.getLine(), t.getCharPositionInLine(), t.getStartIndex());
    }

    /** Default-channel tokens as `NAME(lexeme)@start..stop:line:column`, joined by spaces. */
    private static String rendered(String src) {
        StringBuilder sb = new StringBuilder();
        for (Delivered d : lex(src).tokens()) {
            if (!d.defaultChannel()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(d.name()).append('(').append(d.lexeme()).append(")@").append(d.start()).append("..").append(d.stop()).append(':').append(d.line()).append(':').append(d.column());
        }
        return sb.toString();
    }

    /** Hidden tokens as `NAME(lexeme)@start..stop`, used for comment and newline pinning. */
    private static String renderedHidden(String src) {
        StringBuilder sb = new StringBuilder();
        for (Delivered d : lex(src).tokens()) {
            if (d.defaultChannel()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(d.name()).append('(').append(escape(d.lexeme())).append(")@").append(d.start()).append("..").append(d.stop());
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /** The failing offsets reported for one input, in callback order. */
    private static List<Integer> failingOffsets(String src) {
        List<Integer> out = new ArrayList<>();
        for (LexError e : lex(src).errors()) {
            out.add(e.failingOffset());
        }
        return out;
    }

    private static List<String> errorMessages(String src) {
        List<String> out = new ArrayList<>();
        for (LexError e : lex(src).errors()) {
            out.add(e.message());
        }
        return out;
    }

    // ---- Offsets, lines, and columns of ordinary tokens ----------------------------------------

    @Test
    public void ordinaryTokensCarryExactOffsetsAndPositions() {
        assertThat(rendered("val x = 1")).isEqualTo("VAL(val)@0..2:1:0 Identifier(x)@4..4:1:4 ASSIGN(=)@6..6:1:6 INTEGER_LITERAL(1)@8..8:1:8");
    }

    @Test
    public void memberAccessTokensAreSingleWidth() {
        assertThat(rendered("a.b")).isEqualTo("Identifier(a)@0..0:1:0 DOT(.)@1..1:1:1 Identifier(b)@2..2:1:2");
    }

    @Test
    public void endOfInputReportsTheLineAndOffsetAfterTheLastToken() {
        Lexed lexed = lex("val x = 1");
        assertThat(lexed.tokens()).hasSize(4);
        assertThat(lexed.eofLine()).isEqualTo(1);
        assertThat(lexed.eofColumn()).isEqualTo(9);
        assertThat(lexed.eofOffset()).isEqualTo(9);
        assertThat(lexed.errors()).isEmpty();
    }

    @Test
    public void emptyInputDeliversNoTokensAndAnOffsetZeroEnd() {
        Lexed lexed = lex("");
        assertThat(lexed.tokens()).isEmpty();
        assertThat(lexed.eofOffset()).isEqualTo(0);
        assertThat(lexed.errors()).isEmpty();
    }

    @Test
    public void whitespaceOnlyInputDeliversNoSignificantTokens() {
        // Spaces and tabs are `WS` (skipped outright); every physical newline stays as a hidden
        // NEWLINE token so the insertion stage can see the boundary.
        assertThat(rendered(" ")).isEmpty();
        assertThat(rendered("  \t ")).isEmpty();
        assertThat(renderedHidden(" ")).isEmpty();
        assertThat(renderedHidden("  \t ")).isEmpty();
        assertThat(renderedHidden("\n")).isEqualTo("NEWLINE(\\n)@0..0");
        assertThat(renderedHidden("\n\n")).isEqualTo("NEWLINE(\\n)@0..0 NEWLINE(\\n)@1..1");
        assertThat(lex("\n\n").eofLine()).isEqualTo(3);
        assertThat(lex("\n\n").errors()).isEmpty();
    }

    // ---- Hidden channel: newlines and comments -----------------------------------------------

    @Test
    public void lineCommentAndItsNewlineAreSeparateHiddenTokens() {
        assertThat(renderedHidden("//c\nx")).isEqualTo("LINE_COMMENT(//c)@0..2 NEWLINE(\\n)@3..3");
        assertThat(rendered("//c\nx")).isEqualTo("Identifier(x)@4..4:2:0");
    }

    @Test
    public void blockCommentIsHiddenAndDoesNotAdvanceTheLineCounterWithoutANewline() {
        assertThat(renderedHidden("/*c*/x")).isEqualTo("BLOCK_COMMENT(/*c*/)@0..4");
        assertThat(rendered("/*c*/x")).isEqualTo("Identifier(x)@5..5:1:5");
    }

    @Test
    public void blockCommentBodyNewlinesAreInsideTheCommentToken() {
        // The comment is one hidden token whose body contains the newline: no separate NEWLINE token
        // is emitted for it, which is why the insertion stage must inspect the comment text itself.
        assertThat(renderedHidden("/*a\nb*/x")).isEqualTo("BLOCK_COMMENT(/*a\\nb*/)@0..6");
        // The line counter still advances across the body, so the following token is positioned on
        // the line the comment ended on, at the column where the comment closed.
        assertThat(rendered("/*a\nb*/x")).isEqualTo("Identifier(x)@7..7:2:3");
    }

    @Test
    public void carriageReturnPairsAndSinglesAreSingleNewlineTokens() {
        assertThat(renderedHidden("a\r\nb")).isEqualTo("NEWLINE(\\r\\n)@1..2");
        assertThat(renderedHidden("a\rb")).isEqualTo("NEWLINE(\\r)@1..1");
    }

    @Test
    public void emptyCommentsAreStillOneHiddenToken() {
        assertThat(renderedHidden("//")).isEqualTo("LINE_COMMENT(//)@0..1");
        assertThat(renderedHidden("/**/")).isEqualTo("BLOCK_COMMENT(/**/)@0..3");
        assertThat(renderedHidden("/***/")).isEqualTo("BLOCK_COMMENT(/***/)@0..4");
    }

    // ---- Unterminated comment is not a comment ------------------------------------------------

    @Test
    public void unterminatedBlockCommentIsNotATokenOfItsOwnKind() {
        // A block comment requires a closing `*/`; without one the characters are ordinary operators.
        assertThat(rendered("/* unterminated")).isEqualTo("DIV(/)@0..0:1:0 MUL(*)@1..1:1:1 Identifier(unterminated)@3..14:1:3");
        assertThat(renderedHidden("/* unterminated")).isEmpty();
    }

    @Test
    public void unterminatedTwoCharacterCommentSplitsIntoOperators() {
        assertThat(rendered("/*")).isEqualTo("DIV(/)@0..0:1:0 MUL(*)@1..1:1:1");
        assertThat(rendered("/*/")).isEqualTo("DIV(/)@0..0:1:0 MUL(*)@1..1:1:1 DIV(/)@2..2:1:2");
    }

    @Test
    public void commentBodyIsInertButItsDelimitersAreRealOnceClosed() {
        // A quote inside a comment body is content; the comment still closes at the first `*/`.
        assertThat(renderedHidden("/* \"*/ x")).isEqualTo("BLOCK_COMMENT(/* \"*/)@0..5");
        assertThat(rendered("/* \"*/ x")).isEqualTo("Identifier(x)@7..7:1:7");
    }

    // ---- Numeric forms and their boundaries ----------------------------------------------------

    @Test
    public void floatingPointFormsAndTheirOffsets() {
        assertThat(rendered("1.5e+3f")).isEqualTo("FLOATING_LITERAL(1.5e+3f)@0..6:1:0");
        assertThat(rendered("1e+3f")).isEqualTo("FLOATING_LITERAL(1e+3f)@0..4:1:0");
        assertThat(rendered("1E3F")).isEqualTo("FLOATING_LITERAL(1E3F)@0..3:1:0");
        assertThat(rendered("0.0")).isEqualTo("FLOATING_LITERAL(0.0)@0..2:1:0");
        assertThat(rendered("00.00")).isEqualTo("FLOATING_LITERAL(00.00)@0..4:1:0");
        assertThat(rendered("0e0")).isEqualTo("FLOATING_LITERAL(0e0)@0..2:1:0");
    }

    @Test
    public void longSuffixBelongsToIntegerLiteralsOnly() {
        // A `Long` literal is `[0-9]+ [Ll]`; the floating rules carry only an `f`/`F` suffix, so a
        // float followed by `L` is a float and an identifier, never one token.
        assertThat(rendered("1.5L")).isEqualTo("FLOATING_LITERAL(1.5)@0..2:1:0 Identifier(L)@3..3:1:3");
        assertThat(rendered("1.5l")).isEqualTo("FLOATING_LITERAL(1.5)@0..2:1:0 Identifier(l)@3..3:1:3");
        assertThat(rendered("1L.5")).isEqualTo("LONG_LITERAL(1L)@0..1:1:0 DOT(.)@2..2:1:2 INTEGER_LITERAL(5)@3..3:1:3");
    }

    @Test
    public void incompleteExponentAndExtraFractionSplitAtTheLongestValidLiteral() {
        assertThat(rendered("1.5e")).isEqualTo("FLOATING_LITERAL(1.5)@0..2:1:0 Identifier(e)@3..3:1:3");
        assertThat(rendered("1.0.0")).isEqualTo("FLOATING_LITERAL(1.0)@0..2:1:0 DOT(.)@3..3:1:3 INTEGER_LITERAL(0)@4..4:1:4");
        assertThat(rendered("1e3.5")).isEqualTo("FLOATING_LITERAL(1e3)@0..2:1:0 DOT(.)@3..3:1:3 INTEGER_LITERAL(5)@4..4:1:4");
        assertThat(rendered("1.5fF")).isEqualTo("FLOATING_LITERAL(1.5f)@0..3:1:0 Identifier(F)@4..4:1:4");
    }

    // ---- Normal-string escape coverage at the lexer layer --------------------------------------

    @Test
    public void everyEscapeBodyIsKeptInsideTheStringToken() {
        // The lexer admits any single escaped character; the supported set is enforced later, so the
        // only lexer-level requirement is that the literal stays one token.
        for (char c : new char[] {'a', 'b', 'c', 'e', 'f', 'v', 'x', 'n', 'r', 't', '0', '\\', '"'}) {
            String src = "\"\\" + c + "\"";
            assertThat(rendered(src)).as(src).isEqualTo("STRING_LITERAL(\"" + "\\" + c + "\")@0..3:1:0");
        }
    }

    @Test
    public void multiCharacterEscapeBodyStillLeavesOneStringToken() {
        assertThat(rendered("\"\\x41\"")).isEqualTo("STRING_LITERAL(\"\\x41\")@0..5:1:0");
    }

    // ---- Character-literal failures ------------------------------------------------------------

    @Test
    public void emptyAndMultiCharacterAndTruncatedCharacterLiteralsAreLexerFailures() {
        assertThat(lex("''").tokens()).isEmpty();
        assertThat(failingOffsets("''")).isEqualTo(List.of(0));
        assertThat(lex("'a").tokens()).isEmpty();
        assertThat(failingOffsets("'a")).isEqualTo(List.of(0));
        // `'AB'` fails twice: the body cannot be a character literal, and the trailing quote is then
        // an unterminated one-character literal of its own.
        assertThat(failingOffsets("'AB'")).isEqualTo(List.of(0, 3));
        assertThat(lex("'\\'").tokens()).isEmpty();
        assertThat(failingOffsets("'\\'")).isEqualTo(List.of(0));
    }

    // ---- String-literal failures ---------------------------------------------------------------

    @Test
    public void unterminatedStringIsAFailureAtItsOpeningQuote() {
        assertThat(lex("\"").tokens()).isEmpty();
        assertThat(failingOffsets("\"")).isEqualTo(List.of(0));
        // A trailing backslash consumes the closing quote, so the whole remainder fails as one run.
        assertThat(lex("\"\\").tokens()).isEmpty();
        assertThat(failingOffsets("\"\\")).isEqualTo(List.of(0));
        assertThat(errorMessages("\"abc")).containsExactly("token recognition error at: '\"abc'");
    }

    // ---- Raw-string delimiter accounting -------------------------------------------------------

    @Test
    public void rawStringClosesOnlyOnTheOpeningHashCount() {
        assertThat(rendered("r####\"a\"####")).isEqualTo("RAW_STRING_LITERAL(r####\"a\"####)@0..11:1:0");
        assertThat(rendered("r#####\"\"#####")).isEqualTo("RAW_STRING_LITERAL(r#####\"\"#####)@0..12:1:0");
    }

    @Test
    public void extraClosingHashesLeaveTheLiteralUnterminated() {
        // The opening delimiter fixes N=0 here, so a following `#` is never part of the closer and the
        // scan runs to end of input.
        assertThat(rendered("r\"\"#")).isEqualTo("RAW_STRING_LITERAL(r\"\"#)@0..3:1:0");
        assertThat(errorMessages("r\"\"#")).containsExactly("unterminated raw string literal; expected closing delimiter \"");
        assertThat(rendered("r\"\"##")).isEqualTo("RAW_STRING_LITERAL(r\"\"##)@0..4:1:0");
        assertThat(errorMessages("r\"\"##")).containsExactly("unterminated raw string literal; expected closing delimiter \"");
    }

    @Test
    public void unterminatedRawStringReportsAtTheOpeningDelimiterWithoutAFailingOffset() {
        // The raw-string body scanner reports a dedicated exception carrying the opening token, so it
        // is not a `LexerNoViableAltException` and has no failing-offset value.
        Lexed lexed = lex("r\"");
        assertThat(lexed.tokens()).hasSize(1);
        assertThat(lexed.errors()).hasSize(1);
        assertThat(lexed.errors().get(0).failingOffset()).isEqualTo(-1);
        assertThat(lexed.errors().get(0).line()).isEqualTo(1);
        assertThat(lexed.errors().get(0).column()).isEqualTo(0);
    }

    @Test
    public void unterminatedCountedRawStringNamesItsOwnClosingDelimiter() {
        assertThat(errorMessages("r#\"")).containsExactly("unterminated raw string literal; expected closing delimiter \"#");
    }

    @Test
    public void nonContiguousRawPrefixFallsApartIntoIdentifierAndErrors() {
        assertThat(rendered("r #\"a\"#")).isEqualTo("Identifier(r)@0..0:1:0 STRING_LITERAL(\"a\")@3..5:1:3");
        assertThat(failingOffsets("r #\"a\"#")).isEqualTo(List.of(2, 6));
    }

    @Test
    public void rawPrefixSeparatedByANewlineIsAnIdentifierAndAPlainString() {
        assertThat(rendered("r\n\"a\"")).isEqualTo("Identifier(r)@0..0:1:0 STRING_LITERAL(\"a\")@2..4:2:0");
        assertThat(lex("r\n\"a\"").errors()).isEmpty();
    }

    // ---- Characters outside the token alphabet -------------------------------------------------

    @Test
    public void everyUnrecognizedCharacterIsReportedAtItsOwnOffset() {
        assertThat(failingOffsets("@@@")).isEqualTo(List.of(0, 1, 2));
        assertThat(failingOffsets("@ # $ % ~")).isEqualTo(List.of(0, 2, 4, 6, 8));
        assertThat(failingOffsets("a\\b")).isEqualTo(List.of(1));
    }

    @Test
    public void nonAsciiWhitespaceAndFormattingCharactersAreNotWhitespace() {
        // U+00A0 (no-break space) and U+2028 (line separator) are neither `WS` nor a line terminator,
        // and neither is an identifier character.
        assertThat(failingOffsets("a\u00A0b")).isEqualTo(List.of(1));
        assertThat(rendered("a\u00A0b")).isEqualTo("Identifier(a)@0..0:1:0 Identifier(b)@2..2:1:2");
        assertThat(failingOffsets("a\u2028b")).isEqualTo(List.of(1));
    }

    @Test
    public void byteOrderMarkIsNotWhitespaceAndNotAnIdentifierCharacter() {
        assertThat(failingOffsets("\uFEFFx")).isEqualTo(List.of(0));
        assertThat(rendered("\uFEFFx")).isEqualTo("Identifier(x)@1..1:1:1");
    }

    @Test
    public void verticalTabAndFormFeedAreHorizontalWhitespaceNotLineTerminators() {
        // Section 16 counts only CR, LF, and CRLF as physical newlines; `WS` covers the other two
        // ASCII control spaces, so they separate tokens without starting a new line.
        assertThat(rendered("1\u000B2")).isEqualTo("INTEGER_LITERAL(1)@0..0:1:0 INTEGER_LITERAL(2)@2..2:1:2");
        assertThat(rendered("1\u000C2")).isEqualTo("INTEGER_LITERAL(1)@0..0:1:0 INTEGER_LITERAL(2)@2..2:1:2");
        assertThat(renderedHidden("1\u000B2")).isEmpty();
        assertThat(lex("1\u000B2").eofLine()).isEqualTo(1);
    }

    // ---- Astral characters ----------------------------------------------------------------------

    @Test
    public void astralCharactersCountAsOneCharacterLiteralBody() {
        // U+1D11E is one Unicode scalar value encoded as a UTF-16 surrogate pair, so the literal spans
        // three code units: quote, pair, quote.
        String clef = "\uD834\uDD1E";
        assertThat(rendered("'" + clef + "'")).isEqualTo("CHARACTER_LITERAL('" + clef + "')@0..2:1:0");
        assertThat(rendered("\"" + clef + "\"")).isEqualTo("STRING_LITERAL(\"" + clef + "\")@0..2:1:0");
        assertThat(rendered("r\"" + clef + "\"")).isEqualTo("RAW_STRING_LITERAL(r\"" + clef + "\")@0..3:1:0");
        assertThat(lex("'" + clef + "'").errors()).isEmpty();
    }

    // ---- Longest match across the whole operator set -------------------------------------------

    @Test
    public void operatorOffsetsComeFromTheLongestMatchingRule() {
        // `<==` must not become `LT` + `ASSIGN`: the longest rule that matches wins even when the
        // resulting token is not what the parser wants at that position.
        assertThat(rendered("<==")).isEqualTo("LE(<=)@0..1:1:0 ASSIGN(=)@2..2:1:2");
        assertThat(rendered(">==")).isEqualTo("GE(>=)@0..1:1:0 ASSIGN(=)@2..2:1:2");
        // `==>=` has no three-character match starting with `==` other than `==` itself, so the rest
        // re-lexes from the third character and the second pair becomes `>=`.
        assertThat(rendered("==>=")).isEqualTo("EQ(==)@0..1:1:0 GE(>=)@2..3:1:2");
        assertThat(rendered("=>")).isEqualTo("ARROW(=>)@0..1:1:0");
        assertThat(rendered("::")).isEqualTo("COLONCOLON(::)@0..1:1:0");
        assertThat(rendered("&&")).isEqualTo("AND(&&)@0..1:1:0");
        assertThat(rendered("||")).isEqualTo("OR(||)@0..1:1:0");
        assertThat(rendered("<=")).isEqualTo("LE(<=)@0..1:1:0");
        assertThat(rendered(">=")).isEqualTo("GE(>=)@0..1:1:0");
        assertThat(rendered("===")).isEqualTo("EQEQ(===)@0..2:1:0");
        assertThat(rendered("!==")).isEqualTo("NEQEQ(!==)@0..2:1:0");
        assertThat(rendered("==")).isEqualTo("EQ(==)@0..1:1:0");
        assertThat(rendered("!=")).isEqualTo("NEQ(!=)@0..1:1:0");
    }

    @Test
    public void questionAndDotSequencesNeverSplitIntoShorterMatches() {
        assertThat(rendered("?.")).isEqualTo("NULLABLE_DOT(?.)@0..1:1:0");
        assertThat(rendered("??")).isEqualTo("NULL_COALESCE(??)@0..1:1:0");
        assertThat(rendered("...")).isEqualTo("DOTDOTDOT(...)@0..2:1:0");
        assertThat(rendered("..<")).isEqualTo("DOTDOTLT(..<)@0..2:1:0");
        assertThat(rendered("..>")).isEqualTo("DOTDOTGT(..>)@0..2:1:0");
        assertThat(rendered("..")).isEqualTo("DOTDOT(..)@0..1:1:0");
        assertThat(rendered(".")).isEqualTo("DOT(.)@0..0:1:0");
    }

    // ---- Token inventory ------------------------------------------------------------------------

    /**
     * The grammar declares exactly these token types. A new lexer rule that no test can reach, or a
     * renamed token, fails here first; the symbolic names are the vocabulary the parser and the
     * semicolon-insertion tables are written against.
     */
    @Test
    public void grammarDeclaresExactlyTheExpectedTokenVocabulary() {
        Set<String> declared = new TreeSet<>();
        for (int type = 1; type <= SolvikLexer.VOCABULARY.getMaxTokenType(); type++) {
            String name = SolvikLexer.VOCABULARY.getSymbolicName(type);
            if (name != null && !name.startsWith("T__")) {
                declared.add(name);
            }
        }
        Set<String> expected = new LinkedHashSet<>(List.of( //
                "FUNC", "INCLUDE", "MODULE", "ALIAS", "CLASS", "INTERFACE", "ENUM", "SEALED", "DELEGATE", "IMPLEMENTS", "OPEN", "EXTENDS", "OVERRIDE", "THIS", "SUPER", "VAL", "VAR", "IF", "ELSE", //
                "WHILE", "FOR", "IN", "BREAK", "CONTINUE", "RETURN", "MATCH", "ARROW", "SWITCH", "CASE", "DEFAULT", "REGEX_KW", "NULL", "IS", "AS", "BOOL_LITERAL", "Identifier", "INTEGER_LITERAL", //
                "LONG_LITERAL", "FLOATING_LITERAL", "CHARACTER_LITERAL", "STRING_LITERAL", "RAW_STRING_LITERAL", "LPAREN", "RPAREN", "LBRACE", "RBRACE", "SEMI", "ASSIGN", "COLON", "COLONCOLON", "COMMA", //
                "DOT", "DOTDOT", "DOTDOTDOT", "DOTDOTLT", "DOTDOTGT", "NULLABLE_DOT", "NULL_COALESCE", "QUESTION", "LBRACKET", "RBRACKET", "ADD", "SUB", "MUL", "DIV", "BANG", "EQEQ", "NEQEQ", "EQ", "NEQ", //
                "LT", "LE", "GT", "GE", "AND", "OR", "WS", "NEWLINE", "LINE_COMMENT", "BLOCK_COMMENT"));
        assertThat(declared).containsExactlyInAnyOrderElementsOf(expected);
        // Every expected name must resolve to a distinct positive token type: a duplicate resolution
        // would mean the inventory above names a type the grammar no longer declares.
        Set<Integer> types = new TreeSet<>();
        for (String name : expected) {
            types.add(tokenTypeOf(name));
        }
        assertThat(types).hasSize(expected.size());
    }

    private static int tokenTypeOf(String symbolicName) {
        for (int type = 1; type <= SolvikLexer.VOCABULARY.getMaxTokenType(); type++) {
            if (symbolicName.equals(SolvikLexer.VOCABULARY.getSymbolicName(type))) {
                return type;
            }
        }
        throw new AssertionError("no token type named " + symbolicName);
    }
}
