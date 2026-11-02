package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.solvik.compiler.testsupport.Checking;

/** Regression contracts for checker/lowering/language-guide agreement. */
class LanguageConsistencyTest {
    private static Diagnostics check(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        assertThat(diagnostics.all()).describedAs("parse errors for %s", source).isEmpty();
        Checking.check(program, diagnostics);
        return diagnostics;
    }

    private static void accepted(String source) {
        Diagnostics diagnostics = check(source);
        assertThat(diagnostics.all()).describedAs("%s", diagnostics.format("test.sol")).isEmpty();
    }

    @Test
    @DisplayName("LANGUAGE.md §5: an array's length is the immutable member size")
    void array_length_is_the_immutable_size_member() {
        accepted("values := Integer[2] {1, 2}\nTextWriter(Stdio.stdout()).writeLine((values.size) .. \"\")");
        accepted("values := Integer[2] {1, 2}\nTextWriter(Stdio.stdout()).writeLine((values.size .. \" items\") .. \"\")");
        accepted("parts := \"a,b\".split(\",\")\nTextWriter(Stdio.stdout()).writeLine((parts.size) .. \"\")");
        assertThat(check("values := Integer[2] {1, 2}\nTextWriter(Stdio.stdout()).writeLine((values.size()) .. \"\")").all())
                .extracting(d -> d.code()).containsExactly(TypeChecker.TYPE_ERROR);
        assertThat(check("values := Integer[2] {1, 2}\nvalues.size = 3").all())
                .extracting(d -> d.code()).containsExactly(TypeChecker.IMMUTABLE_MEMBER);
    }

    @Test
    void boolean_literals_and_variables_have_the_same_conversion_contract() {
        accepted("TextWriter(Stdio.stdout()).writeLine((Boolean(true)) .. (Boolean(false)) .. (Boolean(\"true\")))");
        accepted("flag := true\nTextWriter(Stdio.stdout()).writeLine((Boolean(flag)) .. \"\")");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Byte[2] {-128, 127}", "Short[2] {-32768, 32767}",
            "Integer[2] {-2147483648, 2147483647}",
            "Long[2] {-9223372036854775808, 9223372036854775807}",
            "BigInteger[1] {999999999999999999999999999999999}",
            "BigDecimal[2] {-999999999999999999999999999999999, -1.5}",
            "Float[2] {-1.5, -1}", "Double[2] {-1.5, -1}"
    })
    void literal_arrays_adopt_their_carrier_before_default_integer_checks(String literal) {
        accepted("values := " + literal);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Byte[1] {-129}", "Byte[1] {128}", "Short[1] {-32769}", "Short[1] {32768}",
            "Integer[1] {-2147483649}", "Integer[1] {2147483648}",
            "Long[1] {-9223372036854775809}", "Long[1] {9223372036854775808}"
    })
    void contextual_literals_still_require_representable_values(String literal) {
        assertThat(check("values := " + literal).all()).extracting(d -> d.code())
                .contains(TypeChecker.STATIC_FAILURE);
    }

    @Test
    void floating_literals_outside_their_carrier_are_diagnostics() {
        String huge = "1" + "0".repeat(400);
        String tiny = "0." + "0".repeat(400) + "1";
        for (String value : new String[]{huge, tiny}) {
            for (String carrier : new String[]{"Float", "Double"}) {
                assertThat(check("values := " + carrier + "[1] {" + value + "}").all())
                        .extracting(d -> d.code()).contains(TypeChecker.STATIC_FAILURE);
            }
        }
        assertThat(check("value := " + huge + ".0").all()).extracting(d -> d.code())
                .contains(TypeChecker.STATIC_FAILURE);
        accepted("values := BigDecimal[1] {" + tiny + "}");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "TextWriter(Stdio.stdout()).writeLine((nothing()) .. \"\")", "TextWriter(Stdio.stdout()).write((nothing()) .. \"\")", "TextWriter(Stdio.stdout()).writeLine((\"x\" .. nothing()) .. \"\")",
            "TextWriter(Stdio.stdout()).writeLine((nothing() .. \"x\") .. \"\")", "x := nothing()",
            "mut x: Integer? = Null\nx = nothing()", "x := Any[1] {nothing()}",
            "func take(x: Any) {}\ntake(nothing())", "TextWriter(Stdio.stdout()).writeLine((nothing().type()) .. \"\")",
            "TextWriter(Stdio.stdout()).writeLine((1.equals(nothing())) .. \"\")", "TextWriter(Stdio.stdout()).writeLine((String(nothing())) .. \"\")",
            "TextWriter(Stdio.stdout()).writeLine((nothing() == Null) .. \"\")"
    })
    void void_calls_are_rejected_in_every_value_context(String use) {
        assertThat(check("func nothing() {}\n" + use).all()).extracting(d -> d.code())
                .contains(TypeChecker.TYPE_ERROR);
    }

    @Test
    void void_calls_remain_valid_statements() {
        accepted("func nothing() {}\nnothing()\nTextWriter(Stdio.stdout()).writeLine(\"\")\nTextWriter(Stdio.stdout()).write(\"\")\nexit()");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "pub func equals() -> Boolean { return true }",
            "pub func equals(other: String) -> Boolean { return true }",
            "pub func equals(other: Any) -> String { return \"true\" }",
            "pub func equals(other: Any) {}",
            "pub func hashCode(x: Integer) -> Integer { return x }",
            "pub func hashCode() -> Long { return Long(1) }",
            "pub func toString(x: String) -> String { return x }",
            "pub func toString() -> Integer { return 1 }"
    })
    void invalid_object_contract_signatures_are_solvik_errors(String method) {
        assertThat(check("type Item { pub func Item() {}\n" + method + " }").all())
                .extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
    }

    @Test
    void equals_can_compare_private_members_of_its_checked_peer() {
        accepted("""
                type Item<T> {
                    mut value: T? = Null

                    pub func Item(value: T) {
                        self.value = value
                    }

                    func has(value: T?) -> Boolean {
                        mut mine: T? = self.value
                        if mine == Null {
                            return value == Null
                        }
                        return mine.equals(value)
                    }

                    pub func equals(other: Any?) -> Boolean {
                        return other.has(self.value) && self.has(other.value)
                    }
                }
                TextWriter(Stdio.stdout()).writeLine((Item(1).equals(Item(1))) .. "")
                """);
    }

    @Test
    void distinct_user_types_can_be_compared_and_are_never_identical() {
        accepted("""
                type Foo { pub func Foo() {} }
                type Bar { pub func Bar() {} }
                x := Foo()
                y := Bar()
                TextWriter(Stdio.stdout()).writeLine((x == y) .. (" ") .. (x.equals(y)))
                """);
    }

    @Test
    void signed_byte_switch_cases_match_the_carrier() {
        accepted("switch Byte(-128) { case -128 {} case -1 {} case 0 {} case 127 {} }");
        for (int label : new int[]{-129, 128, 200, 255, 256}) {
            assertThat(check("switch Byte(0) { case " + label + " {} }").all())
                    .extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        }
    }

    @Test
    void external_append_requires_a_mutable_string_field() {
        String type = "type Item { pub mut number := 1\npub mut text := \"a\"\n"
                + "pub fixed := \"b\"\npub func Item() {} }\nitem := Item()\n";
        accepted(type + "item.text ..= 1");
        assertThat(check(type + "item.number ..= 1").all()).extracting(d -> d.code())
                .contains(TypeChecker.TYPE_ERROR);
        assertThat(check(type + "item.fixed ..= 1").all()).extracting(d -> d.code())
                .contains(TypeChecker.IMMUTABLE_MEMBER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "1.0", "2147483648", "9223372036854775807",
            "9223372036854775808", "999999999999999999999999999999999999999999"})
    void invalid_array_dimensions_are_diagnostics_not_compiler_exceptions(String size) {
        assertThat(check("values := Integer?[" + size + "] {}").all())
                .extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
    }

    @Test
    void dimension_validation_does_not_allocate_arrays() {
        accepted("values := Integer?[1] {}");
        accepted("values := Integer?[2147483647] {}");
        assertThat(check("size := 1\nvalues := Integer?[size] {}").all())
                .extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
    }

    @Test
    void small_integral_indexes_work_but_nonliteral_widening_is_not_assignment() {
        accepted("values := Integer[1] {1}\nTextWriter(Stdio.stdout()).writeLine((values[Short(0)]) .. \"\")\nvalues[Short(0)] = 2");
        assertThat(check("values := Integer[1] {1}\nTextWriter(Stdio.stdout()).writeLine((values[Long(0)]) .. \"\")").all())
                .extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        assertThat(check("value := 1\nvalues: Long[1] = Long[1] {value}").all())
                .extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        assertThat(check("TextWriter(Stdio.stdout()).writeLine((2147483648) .. \"\")").all()).extracting(d -> d.code())
                .contains(TypeChecker.STATIC_FAILURE);
    }

    @Test
    void a_nullable_subject_and_unsupported_switch_carriers_are_rejected() {
        assertThat(check("slot: Integer? = Null\nswitch slot { case 1 {} }").all())
                .extracting(d -> d.code()).contains(TypeChecker.NULL_UNSAFE);
        for (String source : new String[]{
                "switch Short(1) { case 1 {} }", "switch Long(1) { case 1 {} }",
                "switch BigInteger(1) { case 1 {} }", "switch BigDecimal(1) { case 1 {} }"}) {
            assertThat(check(source).all()).extracting(d -> d.code()).contains(TypeChecker.TYPE_ERROR);
        }
    }
}
