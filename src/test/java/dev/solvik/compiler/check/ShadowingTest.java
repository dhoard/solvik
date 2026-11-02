package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import dev.solvik.compiler.testsupport.Checking;

/** Block scoping, shadowing, and the {@code outer} qualifier (§3, "Scope and shadowing"). */
@DisplayName("Type checker / scope and shadowing")
class ShadowingTest {

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
    void a_nested_block_shadows_an_enclosing_variable() {
        assertAccepted("""
                mut x := 1
                {
                    mut x := 2
                    TextWriter(Stdio.stdout()).writeLine((x) .. "")
                }
                TextWriter(Stdio.stdout()).writeLine((x) .. "")
                """);
    }

    @Test
    void outer_reads_the_shadowed_binding() {
        assertAccepted("""
                mut x := 1
                {
                    mut x := outer x + 1
                    TextWriter(Stdio.stdout()).writeLine((x) .. "")
                }
                """);
    }

    @Test
    void outer_assigns_through_to_the_shadowed_binding() {
        assertAccepted("""
                mut count := 0
                {
                    mut count := 10
                    outer count = outer count + 1
                    TextWriter(Stdio.stdout()).writeLine((count) .. "")
                }
                """);
    }

    @Test
    void shadows_may_stack_and_outer_steps_out_one_scope() {
        assertAccepted("""
                mut x := 1
                {
                    mut x := 2
                    {
                        mut x := outer x + 10
                        TextWriter(Stdio.stdout()).writeLine((x) .. "")
                    }
                }
                """);
    }

    @Test
    void a_shadow_may_have_a_different_type() {
        assertAccepted("""
                value := 1
                {
                    value := "text"
                    TextWriter(Stdio.stdout()).writeLine((value) .. "")
                }
                TextWriter(Stdio.stdout()).writeLine((value) .. "")
                """);
    }

    @Test
    void outer_reaches_parameters_and_loop_bindings() {
        assertAccepted("""
                func describe(item: Integer) -> String {
                    {
                        item := "shadow"
                        return item .. " over " .. outer item
                    }
                }
                TextWriter(Stdio.stdout()).writeLine((describe(3)) .. "")
                """);
        assertAccepted("""
                items := Integer[2] {1, 2}
                for item in items {
                    item := item * 100
                    TextWriter(Stdio.stdout()).writeLine((item) .. "")
                }
                """);
        assertAccepted("""
                items := Integer[1] {7}
                for item in items {
                    TextWriter(Stdio.stdout()).writeLine((item) .. (" ") .. (outer item))
                }
                """);
    }

    @Test
    void a_shadowed_binding_can_be_read_through_outer() {
        assertAccepted("""
                mut slot: Integer? = Null
                slot = 5
                {
                    mut slot := 9
                    TextWriter(Stdio.stdout()).writeLine((slot) .. (" ") .. (outer slot))
                }
                """);
    }

    @Test
    void redeclaring_a_name_in_the_same_scope_is_rejected() {
        assertRejected("""
                mut x := 1
                mut x := 2
                TextWriter(Stdio.stdout()).writeLine((x) .. "")
                """);
        assertRejected("""
                mut x := 1
                {
                    y := 2
                    y := 3
                    TextWriter(Stdio.stdout()).writeLine((x) .. (y))
                }
                """);
    }

    @Test
    @DisplayName("a for body is a scope, so it may shadow both its binding and an outer name")
    void a_loop_body_may_shadow_its_own_binding() {
        assertAccepted("""
                items := Integer[1] {1}
                for item in items {
                    item := 2
                    TextWriter(Stdio.stdout()).writeLine((item) .. "")
                }
                """);
        assertAccepted("""
                items := Integer[1] {1}
                for item in items {
                    items := item
                    TextWriter(Stdio.stdout()).writeLine((items) .. "")
                }
                """);
    }

    @Test
    void outer_without_an_enclosing_binding_is_rejected() {
        assertRejected("""
                mut x := 1
                TextWriter(Stdio.stdout()).writeLine((outer x) .. "")
                """);
        assertRejected("""
                mut x := 1
                {
                    TextWriter(Stdio.stdout()).writeLine((outer y) .. "")
                }
                """);
    }

    @Test
    void outer_cannot_reach_a_binding_that_no_enclosing_scope_declares() {
        assertRejected("""
                {
                    mut slot := 9
                    TextWriter(Stdio.stdout()).writeLine((outer slot) .. "")
                }
                """);
    }

    @Test
    void outer_respects_the_shadowed_type() {
        assertRejected("""
                mut x := 1
                {
                    mut x := "text"
                    outer x = "not an Integer"
                }
                """);
    }

    @Test
    void a_shadow_cannot_assign_the_wrong_type_either() {
        assertRejected("""
                mut x := 1
                {
                    mut x := "text"
                    x = 5
                }
                """);
    }

    @Test
    void outer_is_a_reserved_word() {
        assertRejected("""
                outer := 1
                TextWriter(Stdio.stdout()).writeLine((outer) .. "")
                """);
    }
}
