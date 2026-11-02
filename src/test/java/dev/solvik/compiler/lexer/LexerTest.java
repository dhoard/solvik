package dev.solvik.compiler.lexer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.diagnostic.Diagnostic;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.diagnostic.SourcePos;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Lexer")
class LexerTest {

    private static List<Token> lex(String source) {
        return new Lexer(source, new Diagnostics()).tokenize();
    }

    private static List<TokenKind> kinds(String source) {
        return lex(source).stream().map(Token::kind).toList();
    }

    private static Diagnostics lexWithDiagnostics(String source) {
        Diagnostics diagnostics = new Diagnostics();
        new Lexer(source, diagnostics).tokenize();
        return diagnostics;
    }

    private static List<String> errorCodes(String source) {
        return lexWithDiagnostics(source).all().stream()
                .filter(Diagnostic::isError)
                .map(Diagnostic::code)
                .toList();
    }

    @Nested
    @DisplayName("names, keywords, and literals")
    class NamesAndLiterals {

        @Test
        void separates_keywords_from_identifiers() {
            assertThat(kinds("count := 1"))
                    .containsExactly(TokenKind.IDENTIFIER, TokenKind.DECLARE,
                            TokenKind.INT_LITERAL, TokenKind.EOF);
            assertThat(kinds("type func if else while for in return true false Null null var"))
                    .containsExactly(TokenKind.TYPE, TokenKind.FUNC, TokenKind.IF, TokenKind.ELSE,
                            TokenKind.WHILE, TokenKind.FOR, TokenKind.IN, TokenKind.RETURN,
                            TokenKind.TRUE, TokenKind.FALSE, TokenKind.NULL,
                            TokenKind.NULL_LOWER, TokenKind.VAR, TokenKind.EOF);
        }

        @Test
        @DisplayName("switch, case, regex, and default are keywords")
        void switch_keywords_are_reserved() {
            assertThat(kinds("switch case regex default")).containsExactly(
                    TokenKind.SWITCH, TokenKind.CASE, TokenKind.REGEX, TokenKind.DEFAULT,
                    TokenKind.EOF);
        }

        @Test
        @DisplayName("static, pub, and mut are declaration keywords")
        void declaration_modifiers_are_reserved() {
            assertThat(kinds("static pub mut")).containsExactly(
                    TokenKind.STATIC, TokenKind.PUB, TokenKind.MUT, TokenKind.EOF);
        }

        @Test
        @DisplayName("try, catch, finally, and raise are exception keywords")
        void exception_keywords_are_reserved() {
            assertThat(kinds("try catch finally raise")).containsExactly(
                    TokenKind.TRY, TokenKind.CATCH, TokenKind.FINALLY, TokenKind.RAISE,
                    TokenKind.EOF);
            assertThat(kinds("tryAgain catcher finallyDone raised")).containsExactly(
                    TokenKind.IDENTIFIER, TokenKind.IDENTIFIER, TokenKind.IDENTIFIER,
                    TokenKind.IDENTIFIER, TokenKind.EOF);
        }

        @Test
        void keyword_prefixes_are_ordinary_identifiers() {
            assertThat(kinds("variable returnValue inPlace staticValue")).containsExactly(
                    TokenKind.IDENTIFIER, TokenKind.IDENTIFIER, TokenKind.IDENTIFIER,
                    TokenKind.IDENTIFIER, TokenKind.EOF);
        }

        @Test
        void distinguishes_integer_and_fractional_literals() {
            assertThat(kinds("10 1.5 0 007")).containsExactly(
                    TokenKind.INT_LITERAL, TokenKind.DECIMAL_LITERAL,
                    TokenKind.INT_LITERAL, TokenKind.INT_LITERAL, TokenKind.EOF);
        }

        @Test
        @DisplayName("1..2 lexes as integer, concatenation, integer (§7)")
        void concatenation_wins_over_fractional_part() {
            assertThat(kinds("1..2")).containsExactly(
                    TokenKind.INT_LITERAL, TokenKind.CONCAT, TokenKind.INT_LITERAL, TokenKind.EOF);
            assertThat(kinds("1.2..3")).containsExactly(
                    TokenKind.DECIMAL_LITERAL, TokenKind.CONCAT, TokenKind.INT_LITERAL, TokenKind.EOF);
        }
    }

    @Nested
    @DisplayName("operators")
    class Operators {

        @Test
        void matches_the_longest_operator() {
            assertThat(kinds("=== == = != ! <= < >= > ..= .. -> -"))
                    .containsExactly(TokenKind.EQ_IDENTITY, TokenKind.EQ, TokenKind.ASSIGN,
                            TokenKind.NOT_EQ, TokenKind.BANG, TokenKind.LE, TokenKind.LT,
                            TokenKind.GE, TokenKind.GT, TokenKind.CONCAT_ASSIGN, TokenKind.CONCAT,
                            TokenKind.ARROW, TokenKind.MINUS, TokenKind.EOF);
        }

        @Test
        void records_source_positions() {
            List<Token> tokens = lex("var\nx = 1");
            assertThat(tokens.get(0).pos()).isEqualTo(new SourcePos(0, 1, 1));
            assertThat(tokens.get(1).pos()).isEqualTo(new SourcePos(3, 1, 4)); // NEWLINE
            assertThat(tokens.get(2).pos()).isEqualTo(new SourcePos(4, 2, 1)); // x
            assertThat(tokens.get(4).pos()).isEqualTo(new SourcePos(8, 2, 5)); // 1
        }
    }

    @Nested
    @DisplayName("string literals (§3)")
    class Strings {

        @Test
        void decodes_the_closed_escape_set() {
            Token token = lex("\"a\\n\\t\\r\\\"\\\\\\0b\"").get(0);
            assertThat(token.kind()).isEqualTo(TokenKind.STRING_LITERAL);
            assertThat(token.value()).isEqualTo("a\n\t\r\"\\\0b");
        }

        @Test
        void rejects_an_unknown_escape() {
            assertThat(errorCodes("\"bad \\q escape\"")).containsExactly(Lexer.UNKNOWN_ESCAPE);
        }

        @Test
        @DisplayName("an ordinary string stops at the newline and reports it")
        void reports_an_unterminated_string_at_a_newline() {
            assertThat(errorCodes("\"open\nb := 2")).containsExactly(Lexer.UNTERMINATED_STRING);
            assertThat(kinds("\"open\nb := 2")).endsWith(
                    TokenKind.IDENTIFIER, TokenKind.DECLARE,
                    TokenKind.INT_LITERAL, TokenKind.EOF);
        }

        @Test
        @DisplayName("raw strings preserve content and scale their closing delimiter")
        void reads_raw_strings() {
            assertThat(lex("r#\"a \"b\" c\"#").get(0).value()).isEqualTo("a \"b\" c");
            assertThat(lex("r##\"a \"# b\"##").get(0).value()).isEqualTo("a \"# b");
            assertThat(lex("r\"plain\"").get(0).value()).isEqualTo("plain");
            assertThat(lex("r#\"line1\nline2\"#").get(0).value()).isEqualTo("line1\nline2");
            assertThat(errorCodes("r#\"unterminated")).containsExactly(Lexer.UNTERMINATED_RAW_STRING);
        }
    }

    @Nested
    @DisplayName("comments (§9)")
    class Comments {

        @Test
        void discards_line_comments_but_keeps_the_terminating_newline() {
            assertThat(kinds("a := 1 // note\nb := 2")).containsExactly(
                    TokenKind.IDENTIFIER, TokenKind.DECLARE, TokenKind.INT_LITERAL,
                    TokenKind.NEWLINE,
                    TokenKind.IDENTIFIER, TokenKind.DECLARE, TokenKind.INT_LITERAL,
                    TokenKind.EOF);
        }

        @Test
        @DisplayName("block comments nest and their newlines are whitespace")
        void nests_block_comments() {
            String source = "/*\n a comment\n /*\n  another comment\n */\n*/\na := 1";
            assertThat(kinds(source)).containsExactly(
                    TokenKind.NEWLINE, TokenKind.IDENTIFIER,
                    TokenKind.DECLARE, TokenKind.INT_LITERAL, TokenKind.EOF);
        }

        @Test
        void reports_an_unterminated_block_comment() {
            assertThat(errorCodes("/* open /* nested */")).containsExactly(
                    Lexer.UNTERMINATED_BLOCK_COMMENT);
        }

        @Test
        void comment_markers_inside_strings_are_content() {
            assertThat(lex("\"/* not a comment */\"").get(0).value())
                    .isEqualTo("/* not a comment */");
        }
    }

    @Nested
    @DisplayName("newlines and continuation (§9)")
    class NewlinesAndContinuation {

        @Test
        void collapses_consecutive_newlines() {
            assertThat(kinds("a\n\n\nb")).containsExactly(
                    TokenKind.IDENTIFIER, TokenKind.NEWLINE, TokenKind.IDENTIFIER, TokenKind.EOF);
        }

        @Test
        @DisplayName("a leading backslash on the next line cancels the newline")
        void continuation_cancels_the_newline() {
            assertThat(kinds("s := \"a\"\n\\ .. \"b\"")).containsExactly(
                    TokenKind.IDENTIFIER, TokenKind.DECLARE, TokenKind.STRING_LITERAL,
                    TokenKind.CONCAT, TokenKind.STRING_LITERAL, TokenKind.EOF);
        }

        @Test
        void continuation_crosses_blank_lines_and_comments() {
            assertThat(kinds("s := \"a\"\n\n/* note */\n\\ .. \"b\"")).containsExactly(
                    TokenKind.IDENTIFIER, TokenKind.DECLARE, TokenKind.STRING_LITERAL,
                    TokenKind.CONCAT, TokenKind.STRING_LITERAL, TokenKind.EOF);
            assertThat(kinds("s := \"a\" // note\n\\ .. \"b\"")).containsExactly(
                    TokenKind.IDENTIFIER, TokenKind.DECLARE, TokenKind.STRING_LITERAL,
                    TokenKind.CONCAT, TokenKind.STRING_LITERAL, TokenKind.EOF);
        }

        @Test
        void a_semicolon_ends_the_statement_even_before_a_continuation_line() {
            assertThat(kinds("a := 1;\n\\ .. \"b\"")).containsExactly(
                    TokenKind.IDENTIFIER, TokenKind.DECLARE, TokenKind.INT_LITERAL,
                    TokenKind.SEMICOLON, TokenKind.CONCAT, TokenKind.STRING_LITERAL, TokenKind.EOF);
        }

        @Test
        void rejects_a_continuation_marker_that_does_not_start_a_line() {
            assertThat(errorCodes("a := \\ 1")).containsExactly(Lexer.MISPLACED_CONTINUATION);
        }
    }

    @Nested
    @DisplayName("errors")
    class Errors {

        @Test
        void reports_unexpected_characters_once_and_continues() {
            assertThat(errorCodes("a := 1 @ 2")).containsExactly(Lexer.UNEXPECTED_CHARACTER);
            assertThat(kinds("a := 1 @ 2")).containsExactly(
                    TokenKind.IDENTIFIER, TokenKind.DECLARE,
                    TokenKind.INT_LITERAL, TokenKind.INT_LITERAL, TokenKind.EOF);
        }

        @Test
        void always_ends_with_eof() {
            assertThat(lex("").getLast().kind()).isEqualTo(TokenKind.EOF);
        }
    }
}
