package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostic;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.lexer.NumericSuffix;
import dev.solvik.compiler.lexer.Token;
import dev.solvik.compiler.lexer.TokenKind;
import dev.solvik.compiler.parser.Parser;
import dev.solvik.compiler.testsupport.Checking;
import dev.solvik.compiler.types.SolvikType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Uppercase numeric-literal suffixes: lexing, suffixed types, ranges, and contextual rules. */
@DisplayName("Numeric literal suffixes")
class NumericSuffixTest {

    private record Checked(Ast.Program program, TypeChecker.Result result, Diagnostics diagnostics) {
        List<String> codes() {
            return diagnostics.all().stream().map(Diagnostic::code).toList();
        }

        List<String> messages() {
            return diagnostics.all().stream().map(Diagnostic::message).toList();
        }
    }

    private static Checked check(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        TypeChecker.Result result = Checking.check(program, diagnostics);
        return new Checked(program, result, diagnostics);
    }

    private static void assertClean(String source) {
        Checked checked = check(source);
        assertThat(checked.diagnostics().all())
                .describedAs("unexpected diagnostics: %s", checked.diagnostics().format("<test>"))
                .isEmpty();
    }

    private static List<String> codes(String source) {
        return check(source).codes();
    }

    private static List<String> messages(String source) {
        return check(source).messages();
    }

    /** The type of the first declared variable, which is the literal in these tests. */
    private static SolvikType literalType(String source) {
        Checked checked = check(source);
        assertThat(checked.diagnostics().all())
                .describedAs("unexpected diagnostics: %s", checked.diagnostics().format("<test>"))
                .isEmpty();
        Ast.VarStmt variable = checked.program().statements().stream()
                .filter(Ast.VarStmt.class::isInstance)
                .map(Ast.VarStmt.class::cast)
                .findFirst()
                .orElseThrow();
        return checked.result().typeOf(variable);
    }

    private static List<Token> lex(String source) {
        return new Lexer(source, new Diagnostics()).tokenize();
    }

    private static List<String> lexCodes(String source) {
        Diagnostics diagnostics = new Diagnostics();
        new Lexer(source, diagnostics).tokenize();
        return diagnostics.all().stream().map(Diagnostic::code).toList();
    }

    @Nested
    @DisplayName("lexing (§4)")
    class Lexing {

        @Test
        void every_suffix_stays_on_its_literal() {
            assertThat(lex("1B").getFirst().kind()).isEqualTo(TokenKind.INT_LITERAL);
            assertThat(lex("1B").getFirst().lexeme()).isEqualTo("1");
            assertThat(lex("1B").getFirst().suffix()).isEqualTo(NumericSuffix.BYTE);
            assertThat(lex("1S").getFirst().suffix()).isEqualTo(NumericSuffix.SHORT);
            assertThat(lex("1I").getFirst().suffix()).isEqualTo(NumericSuffix.INTEGER);
            assertThat(lex("1L").getFirst().suffix()).isEqualTo(NumericSuffix.LONG);
            assertThat(lex("1F").getFirst().suffix()).isEqualTo(NumericSuffix.FLOAT);
            assertThat(lex("1D").getFirst().suffix()).isEqualTo(NumericSuffix.DOUBLE);
            assertThat(lex("1BI").getFirst().suffix()).isEqualTo(NumericSuffix.BIG_INTEGER);
            assertThat(lex("1BD").getFirst().suffix()).isEqualTo(NumericSuffix.BIG_DECIMAL);
            assertThat(lex("1.5BD").getFirst().kind()).isEqualTo(TokenKind.DECIMAL_LITERAL);
            assertThat(lex("1.5BD").getFirst().lexeme()).isEqualTo("1.5");
            assertThat(lex("1.5BD").getFirst().suffix()).isEqualTo(NumericSuffix.BIG_DECIMAL);
        }

        @Test
        @DisplayName("the longest uppercase run is the suffix")
        void longest_match_wins() {
            List<Token> tokens = lex("1BI");
            assertThat(tokens).hasSize(2); // literal and EOF: no stray identifier
            assertThat(tokens.getFirst().suffix()).isEqualTo(NumericSuffix.BIG_INTEGER);
        }

        @Test
        void unsuffixed_literals_are_unchanged() {
            assertThat(lex("42").getFirst().suffix()).isEqualTo(NumericSuffix.NONE);
            assertThat(lex("42").getFirst().lexeme()).isEqualTo("42");
            assertThat(lex("1.5").getFirst().suffix()).isEqualTo(NumericSuffix.NONE);
            assertThat(lex("1..2").stream().map(Token::kind)).containsExactly(
                    TokenKind.INT_LITERAL, TokenKind.CONCAT, TokenKind.INT_LITERAL, TokenKind.EOF);
            assertThat(lex("Integer[3] {1, 2, 3}").get(2).kind()).isEqualTo(TokenKind.INT_LITERAL);
        }

        @Test
        void rejects_an_unknown_suffix() {
            assertThat(lexCodes("1X")).containsExactly(Lexer.INVALID_NUMERIC_SUFFIX);
            Diagnostics diagnostics = new Diagnostics();
            new Lexer("1XYZ", diagnostics).tokenize();
            assertThat(diagnostics.all().getFirst().message())
                    .contains("unknown numeric suffix 'XYZ'", "B, S, I, L, F, D, BI, BD");
        }

        @Test
        @DisplayName("a lowercase suffix names the uppercase spelling")
        void rejects_a_lowercase_suffix() {
            assertThat(lexCodes("1l")).containsExactly(Lexer.INVALID_NUMERIC_SUFFIX);
            assertThat(lex("1l").getFirst().suffix()).isEqualTo(NumericSuffix.LONG);
            Diagnostics diagnostics = new Diagnostics();
            new Lexer("1bd", diagnostics).tokenize();
            assertThat(diagnostics.all().getFirst().message())
                    .contains("uppercase", "'BD' rather than 'bd'");
        }

        @Test
        void a_lowercase_word_that_is_not_a_suffix_stays_an_identifier() {
            assertThat(lexCodes("1x")).isEmpty();
            assertThat(lex("1x").stream().map(Token::kind)).containsExactly(
                    TokenKind.INT_LITERAL, TokenKind.IDENTIFIER, TokenKind.EOF);
        }
    }

    @Nested
    @DisplayName("suffixed literal types (§4)")
    class Types {

        @Test
        void integral_suffixes_select_their_carrier() {
            assertThat(literalType("a := 1B")).isEqualTo(SolvikType.BYTE);
            assertThat(literalType("a := 1S")).isEqualTo(SolvikType.SHORT);
            assertThat(literalType("a := 1I")).isEqualTo(SolvikType.INTEGER);
            assertThat(literalType("a := 1L")).isEqualTo(SolvikType.LONG);
            assertThat(literalType("a := 1BI")).isEqualTo(SolvikType.BIG_INTEGER);
        }

        @Test
        void floating_suffixes_select_their_carrier() {
            assertThat(literalType("a := 1F")).isEqualTo(SolvikType.FLOAT);
            assertThat(literalType("a := 1.5F")).isEqualTo(SolvikType.FLOAT);
            assertThat(literalType("a := 1D")).isEqualTo(SolvikType.DOUBLE);
            assertThat(literalType("a := 1.5D")).isEqualTo(SolvikType.DOUBLE);
            assertThat(literalType("a := 1BD")).isEqualTo(SolvikType.BIG_DECIMAL);
            assertThat(literalType("a := 1.50BD")).isEqualTo(SolvikType.BIG_DECIMAL);
        }

        @Test
        @DisplayName("unsuffixed literals keep their default types")
        void defaults_are_unchanged() {
            assertThat(literalType("a := 1")).isEqualTo(SolvikType.INTEGER);
            assertThat(literalType("a := 1.5")).isEqualTo(SolvikType.DOUBLE);
        }

        @Test
        @DisplayName("a sign folds into a suffixed literal")
        void signed_suffixed_literals_keep_their_type() {
            assertThat(literalType("a := -128B")).isEqualTo(SolvikType.BYTE);
            assertThat(literalType("a := -32768S")).isEqualTo(SolvikType.SHORT);
            assertThat(literalType("a := -2147483648I")).isEqualTo(SolvikType.INTEGER);
            assertThat(literalType("a := -9223372036854775808L")).isEqualTo(SolvikType.LONG);
            assertThat(literalType("a := -1BI")).isEqualTo(SolvikType.BIG_INTEGER);
            assertThat(literalType("a := -1.5BD")).isEqualTo(SolvikType.BIG_DECIMAL);
            assertThat(literalType("a := -1.5F")).isEqualTo(SolvikType.FLOAT);
        }

        @Test
        @DisplayName("a suffix promotes like its constructor form")
        void suffix_types_take_part_in_promotion() {
            assertThat(literalType("a := 1L + 1")).isEqualTo(SolvikType.LONG);
            assertThat(literalType("a := 1B + 1B")).isEqualTo(SolvikType.INTEGER);
            assertThat(literalType("a := 1F + 1")).isEqualTo(SolvikType.FLOAT);
            assertThat(literalType("a := 1BD + 1BD")).isEqualTo(SolvikType.BIG_DECIMAL);
            assertThat(codes("a := 1BI + 1")).contains(TypeChecker.TYPE_ERROR);
        }
    }

    @Nested
    @DisplayName("literal ranges (§4, §10)")
    class Ranges {

        @Test
        void accepts_the_carrier_limits() {
            assertClean("a := 127B\nb := -128B");
            assertClean("a := 32767S\nb := -32768S");
            assertClean("a := 2147483647I\nb := -2147483648I");
            assertClean("a := 9223372036854775807L\nb := -9223372036854775808L");
            assertClean("a := 9999999999999999999999999999999999999999BI");
        }

        @Test
        void rejects_a_value_the_suffix_cannot_hold() {
            assertThat(codes("a := 128B")).containsExactly(TypeChecker.STATIC_FAILURE);
            assertThat(codes("a := -129B")).containsExactly(TypeChecker.STATIC_FAILURE);
            assertThat(codes("a := 32768S")).containsExactly(TypeChecker.STATIC_FAILURE);
            assertThat(codes("a := 3000000000I")).containsExactly(TypeChecker.STATIC_FAILURE);
            assertThat(codes("a := 9223372036854775808L"))
                    .containsExactly(TypeChecker.STATIC_FAILURE);
            assertThat(messages("a := 128B"))
                    .anySatisfy(message -> assertThat(message).contains("Byte literal out of range"));
            assertThat(messages("a := 3000000000"))
                    .anySatisfy(message -> assertThat(message).contains("Integer literal out of range"));
        }

        @Test
        @DisplayName("floating suffixes keep the literal range rule")
        void floating_suffixes_must_be_representable() {
            assertThat(codes("a := 1000000000000000000000000000000000000000F"))
                    .containsExactly(TypeChecker.STATIC_FAILURE);
            assertClean("a := 340000000000000000000000000000000000000D");
            assertClean("a := 123456789012345678901234567890.5BD");
        }

        @Test
        @DisplayName("a fractional literal cannot name an integral carrier")
        void fractional_literals_reject_integral_suffixes() {
            for (String suffix : List.of("B", "S", "I", "L", "BI")) {
                assertThat(codes("a := 1.5" + suffix))
                        .describedAs("suffix %s", suffix)
                        .containsExactly(TypeChecker.TYPE_ERROR);
            }
            assertThat(messages("a := 1.5B"))
                    .anySatisfy(message -> assertThat(message).contains("fractional literal"));
        }
    }

    @Nested
    @DisplayName("array literals (§6)")
    class Arrays {

        @Test
        @DisplayName("unsuffixed elements still adopt the element type")
        void unsuffixed_elements_adopt_the_element_type() {
            assertClean("a := Long[2] {1, 2}");
            assertClean("a := Float[1] {1.5}");
            assertClean("a := Byte[1] {-128}");
        }

        @Test
        void suffixed_elements_keep_their_own_type() {
            assertClean("a := Long[2] {1L, 2L}");
            assertClean("a := Byte[1] {-128B}");
            assertClean("a := Float[1] {1.5F}");
            assertClean("a := BigDecimal[1] {1.5BD}");
            assertClean("a := Number[2] {1L, 2}");
        }

        @Test
        void a_suffixed_element_of_another_type_is_an_error() {
            assertThat(codes("a := Integer[1] {1L}")).containsExactly(TypeChecker.TYPE_ERROR);
            assertThat(messages("a := Integer[1] {1L}"))
                    .anySatisfy(message -> assertThat(message)
                            .contains("cannot place Long in an array of Integer"));
            assertThat(codes("a := Double[1] {1.5F}")).containsExactly(TypeChecker.TYPE_ERROR);
        }

        @Test
        @DisplayName("an array size is an Integer literal")
        void the_size_may_not_carry_another_suffix() {
            assertClean("a := Integer[3I] {1, 2, 3}");
            assertClean("a := Integer[3] {1, 2, 3}");
            assertThat(codes("a := Integer[3L] {1, 2, 3}"))
                    .containsExactly(TypeChecker.TYPE_ERROR);
            assertThat(messages("a := Integer[3L] {1, 2, 3}"))
                    .anySatisfy(message -> assertThat(message)
                            .contains("array length must be an Integer literal"));
        }
    }

    @Nested
    @DisplayName("switch labels (§8)")
    class Labels {

        @Test
        void a_suffixed_label_matches_its_subject_type() {
            assertClean("""
                    b := Byte(1)
                    switch b {
                        case 1B {
                            solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("one") .. "")
                        }
                        case 127I {
                            solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("max") .. "")
                        }
                    }
                    """);
            assertClean("""
                    n := 1
                    switch n {
                        case 1I {
                            solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("one") .. "")
                        }
                    }
                    """);
        }

        @Test
        void a_label_of_another_type_cannot_match() {
            assertThat(codes("""
                    n := 1
                    switch n {
                        case 1L {
                            solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("wide") .. "")
                        }
                    }
                    """)).containsExactly(TypeChecker.TYPE_ERROR);
            assertThat(codes("""
                    n := 1
                    switch n {
                        case 1.5 {
                            solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("fraction") .. "")
                        }
                    }
                    """)).containsExactly(TypeChecker.TYPE_ERROR);
        }

        @Test
        @DisplayName("a suffix does not disguise a duplicate label")
        void duplicate_labels_are_still_detected() {
            assertThat(codes("""
                    n := 1
                    switch n {
                        case 1 {
                            solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("plain") .. "")
                        }
                        case 1I {
                            solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("suffixed") .. "")
                        }
                    }
                    """)).containsExactly(TypeChecker.TYPE_ERROR);
        }
    }

    @Nested
    @DisplayName("conversions (§5)")
    class Conversions {

        @Test
        @DisplayName("a suffixed literal is still a constant conversion argument")
        void conversions_stay_checked() {
            assertClean("a := Integer(1L)");
            assertClean("a := BigDecimal(2BI)");
            assertThat(codes("a := Byte(128B)")).contains(TypeChecker.STATIC_FAILURE);
            assertThat(codes("a := Byte(1B) + 1L")).isEmpty();
        }

        @Test
        void a_suffixed_literal_keeps_its_identity_as_a_receiver() {
            assertClean("a := 1BI.pow(2)");
            assertClean("a := 1.50BD.scale()");
        }
    }

    @Test
    @DisplayName("the parser keeps the suffix on the node and digits in the text")
    void parsed_nodes_carry_the_suffix() {
        Checked checked = check("a := 1BD\nb := 2.5F");
        List<Ast.VarStmt> variables = checked.program().statements().stream()
                .filter(Ast.VarStmt.class::isInstance)
                .map(Ast.VarStmt.class::cast)
                .toList();
        Ast.IntLit integer = (Ast.IntLit) variables.get(0).initializer();
        assertThat(integer.suffix()).isEqualTo(NumericSuffix.BIG_DECIMAL);
        assertThat(integer.text()).isEqualTo("1");
        Ast.DecimalLit decimal = (Ast.DecimalLit) variables.get(1).initializer();
        assertThat(decimal.suffix()).isEqualTo(NumericSuffix.FLOAT);
        assertThat(decimal.text()).isEqualTo("2.5");
    }
}
