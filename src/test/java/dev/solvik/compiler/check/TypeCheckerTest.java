package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import dev.solvik.compiler.testsupport.Checking;
import dev.solvik.compiler.types.SolvikType;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Type checker")
class TypeCheckerTest {

    private record Checked(Ast.Program program, TypeChecker.Result result, Diagnostics diagnostics) {
        List<String> codes() {
            return diagnostics.all().stream().map(d -> d.code()).toList();
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
        return check(source).diagnostics().all().stream().map(d -> d.message()).toList();
    }

    @Nested
    @DisplayName("literals and operators (§3, §7)")
    class Operators {

        @Test
        void default_literal_types_follow_the_table() {
            Checked checked = check("a := 10\nvar b : 1.5\nvar c : \"text\"\nvar d : true");
            assertThat(checked.result().typeOf(firstVar(checked).orElseThrow()))
                    .isEqualTo(SolvikType.INTEGER);
        }

        @Test
        void numeric_operands_widen_and_byte_promotes() {
            assertClean("a := 1 + 1.5");   // Integer widens to Double
            assertClean("b := Byte(100) + Byte(100)"); // Byte promotes to Integer
            assertClean("c := Byte(100) * 2");
        }

        @Test
        void rejects_non_numeric_arithmetic() {
            assertThat(codes("a := 1 + \"x\"")).contains(TypeChecker.TYPE_ERROR);
        }

        @Test
        void rejects_percent_on_decimal() {
            assertThat(codes("a := BigDecimal(\"1.5\") % BigDecimal(\"1.5\")"))
                    .contains(TypeChecker.TYPE_ERROR);
        }

        @Test
        void concatenation_accepts_any_operand() {
            assertClean("a := \"count: \" .. 10");
            assertClean("b := 1 .. \"x\"");
        }

        @Test
        void logical_operators_require_boolean() {
            assertClean("a := true && false || true");
            assertThat(codes("a := 1 && true")).contains(TypeChecker.TYPE_ERROR);
        }
    }

    @Nested
    @DisplayName("declarations and assignment (§3)")
    class Slots {

        @Test
        void a_declaration_always_has_a_value_and_keeps_its_type() {
            assertThat(codes("mut a: Integer? = Null\na = 10")).isEmpty();
            assertThat(codes("var a\nvar b := a")).contains(Parser.REMOVED_SYNTAX);
            assertThat(codes("mut a := 10\na = 1.5")).contains(TypeChecker.TYPE_ERROR);
            assertThat(codes("mut a := 10\na = Null")).contains(TypeChecker.TYPE_ERROR);
        }

        @Test
        void a_declaration_rejects_a_value_of_another_type() {
            assertThat(codes("a: Integer = \"text\"")).contains(TypeChecker.TYPE_ERROR);
            assertThat(codes("mut a: Long = 1L\na = 2")).contains(TypeChecker.TYPE_ERROR);
        }

        @Test
        void append_assignment_requires_a_string_variable() {
            assertClean("mut s := \"a\"\ns ..= \"b\"");
            assertThat(codes("mut n := 1\nn ..= \"b\"")).contains(TypeChecker.TYPE_ERROR);
        }
    }

    @Nested
    @DisplayName("statements (§3)")
    class Statements {

        @Test
        void conditions_must_be_boolean() {
            assertClean("if true { }");
            assertThat(codes("if 1 { }")).contains(TypeChecker.TYPE_ERROR);
            assertThat(codes("while 1 { }")).contains(TypeChecker.TYPE_ERROR);
        }

        @Test
        void a_call_with_no_return_type_is_a_statement_only() {
            assertClean("func note() {\n}\nnote()");
            assertThat(codes("func note() {\n}\nx := note()")).contains(TypeChecker.TYPE_ERROR);
        }

        @Test
        void return_must_match_the_declared_type() {
            assertClean("func f() -> String {\n    return \"x\"\n}");
            assertThat(codes("func f() -> String {\n    return\n}"))
                    .contains(TypeChecker.TYPE_ERROR);
            assertThat(codes("func f() {\n    return 1\n}")).contains(TypeChecker.TYPE_ERROR);
        }

        @Test
        @DisplayName("bare return exits a function with no return type, and a value return exits early")
        void both_return_forms_are_available() {
            assertClean("""
                    func early(target: Integer, values: Integer[]) -> Integer {
                        for v in values {
                            if v == target {
                                return v
                            }
                        }
                        return -1
                    }
                    func announce() {
                        TextWriter(Stdio.stdout()).writeLine(("before") .. "")
                        return
                    }
                    TextWriter(Stdio.stdout()).writeLine((early(20, Integer[2] {20, 30})) .. "")
                    announce()
                    """);
        }

        @Test
        void return_outside_a_function_and_unreachable_code_are_rejected() {
            assertThat(codes("TextWriter(Stdio.stdout()).writeLine((1) .. \"\")\nreturn")).contains(TypeChecker.RETURN_OUTSIDE_FUNCTION);
            assertThat(codes("""
                    func f() {
                        return
                        TextWriter(Stdio.stdout()).writeLine((1) .. "")
                    }
                    """)).contains(TypeChecker.UNREACHABLE);
        }

        @Test
        void calls_are_checked_against_parameter_types() {
            assertClean("""
                    func describe(value: Any, index: Integer) -> String {
                        return value.type()
                    }
                    s := describe(1, 0)
                    """);
            assertThat(codes("""
                    func describe(value: Any, index: Integer) -> String {
                        return value.type()
                    }
                    s := describe(1)
                    """)).contains(TypeChecker.TYPE_ERROR);
        }

        @Test
        void exit_takes_an_optional_status_in_the_byte_range() {
            assertClean("exit()");
            assertClean("exit(0)");
            assertClean("exit(255)");
            assertThat(codes("exit(300)")).contains(TypeChecker.TYPE_ERROR);
            assertThat(codes("exit(\"1\")")).contains(TypeChecker.TYPE_ERROR);
            assertThat(codes("exit(1, 2)")).contains(TypeChecker.TYPE_ERROR);
            assertThat(codes("x := exit()")).contains(TypeChecker.TYPE_ERROR);
        }

        @Test
        void output_calls_accept_any_values_and_null() {
            assertClean("TextWriter(Stdio.stdout()).writeLine((1) .. \"\")\nTextWriter(Stdio.stdout()).writeLine((\"a\") .. (2) .. (true))\nTextWriter(Stdio.stdout()).write(\"\")\nTextWriter(Stdio.stdout()).writeLine((Null) .. \"\")");
            assertClean("x: String? = Null\nTextWriter(Stdio.stdout()).writeLine((x) .. \"\")");
        }
    }

    @Nested
    @DisplayName("arrays (§6)")
    class Arrays {

        @Test
        void literal_elements_take_the_declared_element_type() {
            assertClean("bytes := Byte[3] {1, 2, 127}");
            assertClean("words := String[2] {\"a\", \"b\"}");
            assertClean("mixed := Any[3] {1, \"one\", true}");
        }

        @Test
        @DisplayName("a non-null element type needs exactly the declared length")
        void a_non_null_element_type_needs_every_element() {
            assertThat(codes("bytes := Byte[3] {1, 2}")).contains(TypeChecker.TYPE_ERROR);
            assertThat(messages("bytes := Byte[3] {1, 2}")).anySatisfy(message ->
                    assertThat(message).contains("exactly 3 element(s), found 2"));
            assertThat(codes("none := String[2] { }")).contains(TypeChecker.TYPE_ERROR);
        }

        @Test
        @DisplayName("a nullable element type may list fewer, and the rest are Null")
        void omitted_nullable_elements_are_null() {
            assertClean("bytes := Byte?[3] {1, 2}");
            assertClean("none := String?[2] { }");
            assertThat(codes("bytes := Byte?[2] {1, 2, Null}")).contains(TypeChecker.TYPE_ERROR);
        }

        @Test
        void more_elements_than_the_declared_length_is_an_error() {
            assertThat(codes("bytes := Byte[2] {1, 2, 3}")).contains(TypeChecker.TYPE_ERROR);
            assertThat(messages("bytes := Byte[2] {1, 2, 3}")).anySatisfy(message ->
                    assertThat(message).contains("found 3"));
        }

        @Test
        @DisplayName("an array needs at least one element, so there is no empty array")
        void the_size_must_be_at_least_one() {
            assertThat(codes("empty := Integer[0] { }")).contains(TypeChecker.TYPE_ERROR);
            assertThat(messages("empty := Integer[0] { }"))
                    .anySatisfy(message -> assertThat(message).contains("at least 1"));
        }

        @Test
        @DisplayName("an element type may itself be an array")
        void nested_array_literals_type_check() {
            assertClean("""
                    grid := Integer[][2] {Integer[2] {1, 2}, Integer[2] {3, 4}}
                    row := grid[0]
                    cell := grid[1][0]
                    """);
            assertThat(codes("bad := Integer[][2] {Integer[1] {1}, 2}"))
                    .contains(TypeChecker.TYPE_ERROR);
        }

        @Test
        void indexing_yields_the_element_type_and_size_yields_integer() {
            assertClean("""
                    bytes := Byte[2] {1, 2}
                    first := bytes[0]
                    n := bytes.size
                    """);
            assertThat(codes("n := 1\nx := n[0]")).contains(TypeChecker.TYPE_ERROR);
        }

        @Test
        void for_in_binds_the_element_type() {
            assertClean("""
                    bytes := Byte[2] {1, 2}
                    for b in bytes {
                        TextWriter(Stdio.stdout()).writeLine((b) .. "")
                    }
                    """);
        }
    }

    @Nested
    @DisplayName("declarations")
    class Declarations {

        @Test
        void type_names_must_start_uppercase() {
            assertThat(codes("type point { }")).contains(TypeChecker.INVALID_TYPE_NAME);
            List<String> codes = codes("type Point { }");
            assertThat(codes).doesNotContain(TypeChecker.INVALID_TYPE_NAME);
        }
    }

    private static java.util.Optional<Ast.VarStmt> firstVar(Checked checked) {
        return checked.program().statements().stream()
                .filter(Ast.VarStmt.class::isInstance)
                .map(Ast.VarStmt.class::cast)
                .findFirst();
    }
}
