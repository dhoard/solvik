package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostic;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import dev.solvik.compiler.testsupport.Checking;

/** {@code break} and {@code continue} (§3, "Control flow"). */
@DisplayName("Type checker / break and continue")
class LoopControlTest {

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

    private static void assertRejected(String source, String code) {
        assertThat(check(source).all()).extracting(Diagnostic::code).contains(code);
    }

    @Test
    void break_and_continue_are_accepted_inside_both_loop_forms() {
        assertAccepted("""
                mut n := 0
                while n < 10 {
                    n = n + 1
                    if n == 2 {
                        continue
                    }
                    if n > 4 {
                        break
                    }
                }
                """);
        assertAccepted("""
                items := Integer[3] {1, 2, 3}
                for item in items {
                    if item == 2 {
                        continue
                    }
                    if item == 3 {
                        break
                    }
                }
                """);
    }

    @Test
    void break_and_continue_work_in_nested_loops_in_switches_and_in_methods() {
        assertAccepted("""
                items := Integer[2] {1, 2}
                for item in items {
                    mut inner := 0
                    while inner < 3 {
                        inner = inner + 1
                        if inner == 2 {
                            break
                        }
                    }
                }
                """);
        assertAccepted("""
                items := Integer[2] {1, 2}
                kind := "x"
                for item in items {
                    switch kind {
                        case "x" {
                            break
                        }
                        default {
                            continue
                        }
                    }
                }
                """);
        assertAccepted("""
                type Counter {
                    mut total := 0

                    pub func Counter() {
                        self.total = 0
                    }

                    pub func stopAt(limit: Integer) {
                        mut n := 0
                        while true {
                            n = n + 1
                            if n == limit {
                                break
                            }
                        }
                        self.total = n
                    }
                }
                Counter().stopAt(3)
                """);
    }

    @Test
    @DisplayName("break outside a loop is rejected, including at top level and in a function")
    void break_outside_a_loop_is_rejected() {
        assertRejected("break", TypeChecker.LOOP_CONTROL_OUTSIDE_LOOP);
        assertRejected("""
                func stop() {
                    break
                }
                """, TypeChecker.LOOP_CONTROL_OUTSIDE_LOOP);
        assertRejected("""
                items := Integer[1] {1}
                switch "a" {
                    case "a" {
                        break
                    }
                }
                for item in items {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((item) .. "")
                }
                """, TypeChecker.LOOP_CONTROL_OUTSIDE_LOOP);
    }

    @Test
    void continue_outside_a_loop_is_rejected() {
        assertRejected("continue", TypeChecker.LOOP_CONTROL_OUTSIDE_LOOP);
        assertRejected("""
                if true {
                    continue
                }
                """, TypeChecker.LOOP_CONTROL_OUTSIDE_LOOP);
    }

    @Test
    void statements_after_break_or_continue_are_unreachable() {
        assertRejected("""
                mut n := 0
                while n < 3 {
                    n = n + 1
                    break
                    n = n + 1
                }
                """, TypeChecker.UNREACHABLE);
        assertRejected("""
                items := Integer[1] {1}
                for item in items {
                    continue
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((item) .. "")
                }
                """, TypeChecker.UNREACHABLE);
    }

    @Test
    void a_loop_enclosed_by_a_function_ends_with_break() {
        assertAccepted("""
                func firstEven(items: Any) -> String {
                    mut n := 0
                    while n < 3 {
                        n = n + 1
                        if n == 2 {
                            break
                        }
                    }
                    return n .. ""
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((firstEven(0)) .. "")
                """);
    }

    @Test
    void break_and_continue_are_reserved_words() {
        assertThat(check("break := 1").all()).isNotEmpty();
        assertThat(check("continue := 1").all()).isNotEmpty();
    }
}
