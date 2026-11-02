package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import dev.solvik.compiler.testsupport.Checking;

/** Element assignment {@code a[i] = v} (§6, "Indexing"). */
@DisplayName("Type checker / array element assignment")
class IndexAssignmentTest {

    private static Diagnostics check(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        return diagnostics;
    }

    private static void assertAccepted(String source) {
        Diagnostics result = check(source);
        assertThat(result.all())
                .describedAs("unexpected diagnostics: %s", result.format("<t>")).isEmpty();
    }

    private static void assertRejected(String source) {
        assertThat(check(source).all()).isNotEmpty();
    }

    @Test
    void an_element_takes_a_value_of_the_element_type() {
        assertAccepted("""
                x := Integer[2] {1, 0}
                x[0] = 2
                TextWriter(Stdio.stdout()).writeLine((x[0]) .. "")
                """);
        assertAccepted("""
                words := String[2] {"a", "b"}
                words[0] = "c"
                """);
        assertAccepted("""
                mixed := Any[2] {1, "a"}
                mixed[0] = true
                """);
    }

    @Test
    void nested_elements_and_computed_indices_are_writable() {
        assertAccepted("""
                grid := Integer[][2] {Integer[2] {1, 2}, Integer[2] {3, 4}}
                grid[1][0] = 9
                row := 0
                column := 1
                grid[row][column] = 8
                """);
    }

    @Test
    void append_to_a_string_element_is_accepted_and_to_others_is_not() {
        assertAccepted("""
                words := String[2] {"a", "b"}
                words[1] ..= "!"
                """);
        assertRejected("""
                numbers := Integer[2] {1, 2}
                numbers[0] ..= "!"
                """);
    }

    @Test
    @DisplayName("assignment does not convert, and the widening rule does not apply")
    void the_value_must_already_have_the_element_type() {
        assertRejected("""
                decimals := Double[2] {1.5, 2.5}
                decimals[0] = 1
                """);
        assertRejected("""
                bytes := Byte[2] {1, 2}
                bytes[0] = 3
                """);
        assertAccepted("""
                bytes := Byte[2] {1, 2}
                bytes[0] = Byte(3)
                """);
        assertRejected("""
                numbers := Integer[2] {1, 2}
                numbers[0] = "text"
                """);
    }

    @Test
    void the_index_rules_are_the_read_rules() {
        assertRejected("""
                numbers := Integer[2] {1, 2}
                numbers["a"] = 3
                """);
        assertRejected("""
                numbers := Integer[2] {1, 2}
                numbers[0.5] = 3
                """);
        assertAccepted("""
                numbers := Integer[2] {1, 2}
                bytes := Byte[2] {0, 1}
                numbers[bytes[0]] = 3
                """);
    }

    @Test
    void only_arrays_can_be_indexed_for_assignment() {
        assertRejected("""
                text := "abc"
                text[0] = "x"
                """);
        assertRejected("""
                value := Any[1] {1}
                any := value[0]
                any[0] = 1
                """);
    }

    @Test
    void a_shadowed_array_is_the_one_that_is_written() {
        assertAccepted("""
                numbers := Integer[2] {1, 2}
                {
                    numbers := Integer[2] {5, 6}
                    numbers[0] = 9
                    outer numbers[1] = 8
                }
                """);
    }

    @Test
    void an_element_of_a_parameter_may_be_written() {
        assertAccepted("""
                func fill(items: Integer[]) {
                    items[0] = 9
                }
                fill(Integer[1] {1})
                """);
    }
}
