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

/**
 * {@code raise}, {@code try}, {@code catch}, {@code finally}, and the built-in {@code Exception}
 * ({@code LANGUAGE.md} §7.4).
 *
 * <p>The language has one unchecked exception type, because it has no inheritance: {@code Exception}
 * carries an {@code Integer} code for dispatch and a {@code String} message, a {@code try} statement
 * takes at most one {@code catch} clause and at least one of {@code catch}/{@code finally}, and only
 * a value raised with {@code raise} is catchable.
 */
@DisplayName("exception handling is checked (§7.4)")
class ExceptionTest {

    private static Diagnostics diagnostics(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        return diagnostics;
    }

    private static void assertRejected(String source, String code) {
        assertThat(diagnostics(source).all()).extracting(diagnostic -> diagnostic.code())
                .describedAs("expected %s for: %s", code, source)
                .contains(code);
    }

    private static void assertAccepted(String source) {
        Diagnostics result = diagnostics(source);
        assertThat(result.all())
                .describedAs("unexpected diagnostics: %s", result.format("<t>"))
                .isEmpty();
    }

    @Test
    @DisplayName("all three clause shapes are accepted")
    void the_clause_shapes_are_accepted() {
        assertAccepted("""
                try {
                    TextWriter(Stdio.stdout()).writeLine(("body") .. "")
                } catch (e: Exception) {
                    TextWriter(Stdio.stdout()).writeLine((e.getCode()) .. (" ") .. (e.getMessage()))
                }
                """);
        assertAccepted("""
                try {
                    TextWriter(Stdio.stdout()).writeLine(("body") .. "")
                } finally {
                    TextWriter(Stdio.stdout()).writeLine(("cleanup") .. "")
                }
                """);
        assertAccepted("""
                try {
                    TextWriter(Stdio.stdout()).writeLine(("body") .. "")
                } catch (e: Exception) {
                    TextWriter(Stdio.stdout()).writeLine(("handled") .. "")
                } finally {
                    TextWriter(Stdio.stdout()).writeLine(("cleanup") .. "")
                }
                """);
    }

    @Test
    @DisplayName("Exception is a value type with a code and a message")
    void exception_is_a_built_in_value_type() {
        assertAccepted("""
                raised := Exception(4001, "denied")
                TextWriter(Stdio.stdout()).writeLine((raised.getCode()) .. (" ") .. (raised.getMessage()))
                mut optional: Exception? = Null
                optional = raised
                if optional != Null {
                    TextWriter(Stdio.stdout()).writeLine((optional.getCode()) .. "")
                }
                """);
    }

    @Test
    @DisplayName("a declaration cannot take the built-in Exception name")
    void exception_name_is_reserved() {
        assertRejected("type Exception {\n    pub func Exception() { }\n}\n",
                TypeChecker.TYPE_ERROR);
        assertRejected("module m {\n    pub type Exception {\n        pub func Exception() { }\n"
                + "    }\n}\n", TypeChecker.TYPE_ERROR);
        assertRejected("func Exception(x: Integer) { }\n", TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("a try statement needs a catch or a finally clause")
    void a_try_needs_a_clause() {
        assertRejected("try {\n    TextWriter(Stdio.stdout()).writeLine((\"x\") .. \"\")\n}\n", Parser.EXPECTED_TOKEN);
    }

    @Test
    @DisplayName("a try statement takes at most one catch clause")
    void a_second_catch_is_rejected() {
        assertRejected("""
                try {
                    TextWriter(Stdio.stdout()).writeLine(("x") .. "")
                } catch (e: Exception) {
                    TextWriter(Stdio.stdout()).writeLine(("a") .. "")
                } catch (other: Exception) {
                    TextWriter(Stdio.stdout()).writeLine(("b") .. "")
                }
                """, Parser.EXPECTED_TOKEN);
    }

    @Test
    @DisplayName("the catch clause handles Exception only, and never a nullable one")
    void the_catch_type_is_exception() {
        assertRejected("""
                try {
                    TextWriter(Stdio.stdout()).writeLine(("x") .. "")
                } catch (e: Integer) {
                    TextWriter(Stdio.stdout()).writeLine(("y") .. "")
                }
                """, TypeChecker.CATCH_TYPE);
        assertRejected("""
                try {
                    TextWriter(Stdio.stdout()).writeLine(("x") .. "")
                } catch (e: Exception?) {
                    TextWriter(Stdio.stdout()).writeLine(("y") .. "")
                }
                """, TypeChecker.CATCH_TYPE);
    }

    @Test
    @DisplayName("the catch variable is an immutable Exception visible only in its clause")
    void the_catch_variable_is_scoped_and_immutable() {
        assertAccepted("""
                try {
                    raise Exception(1, "x")
                } catch (e: Exception) {
                    TextWriter(Stdio.stdout()).writeLine((e.getCode()) .. "")
                }
                """);
        assertRejected("""
                try {
                    raise Exception(1, "x")
                } catch (e: Exception) {
                    e = Exception(2, "y")
                }
                """, TypeChecker.IMMUTABLE_MEMBER);
        assertRejected("""
                try {
                    raise Exception(1, "x")
                } catch (e: Exception) {
                    TextWriter(Stdio.stdout()).writeLine((e.getCode()) .. "")
                }
                TextWriter(Stdio.stdout()).writeLine((e.getCode()) .. "")
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("only Exception can be raised, and never a possibly-null value")
    void raise_needs_a_non_null_exception() {
        assertRejected("raise Null\n", TypeChecker.TYPE_ERROR);
        assertRejected("raise 1\n", TypeChecker.TYPE_ERROR);
        assertRejected("mut maybe: Exception? = Null\nraise maybe\n", TypeChecker.NULL_UNSAFE);
        assertAccepted("""
                try {
                    raise Exception(1, "x")
                } catch (e: Exception) {
                    raise e
                }
                """);
    }

    @Test
    @DisplayName("Exception takes exactly an Integer code and a String message")
    void the_constructor_arguments_are_checked() {
        assertRejected("TextWriter(Stdio.stdout()).writeLine((Exception(1)) .. \"\")\n", TypeChecker.TYPE_ERROR);
        assertRejected("TextWriter(Stdio.stdout()).writeLine((Exception(\"4001\", \"denied\")) .. \"\")\n", TypeChecker.TYPE_ERROR);
        assertRejected("TextWriter(Stdio.stdout()).writeLine((Exception(1, Null)) .. \"\")\n", TypeChecker.TYPE_ERROR);
        assertRejected("""
                func maybeCode() -> Integer? { return Null }
                TextWriter(Stdio.stdout()).writeLine((Exception(maybeCode(), "denied")) .. "")
                """, TypeChecker.NULL_UNSAFE);
        assertRejected("""
                func maybeText() -> String? { return Null }
                TextWriter(Stdio.stdout()).writeLine((Exception(4001, maybeText())) .. "")
                """, TypeChecker.NULL_UNSAFE);
    }

    @Test
    @DisplayName("the code and message are functions, not fields")
    void the_members_are_functions() {
        assertRejected("""
                func describe(e: Exception) {
                    TextWriter(Stdio.stdout()).writeLine((e.code) .. "")
                }
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                func describe(e: Exception) {
                    TextWriter(Stdio.stdout()).writeLine((e.getCode(1)) .. "")
                }
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("raise leaves, so what follows it is unreachable")
    void raise_terminates_its_statement_list() {
        assertRejected("""
                func fail() {
                    raise Exception(1, "x")
                    TextWriter(Stdio.stdout()).writeLine(("unreachable") .. "")
                }
                """, TypeChecker.UNREACHABLE);
        assertAccepted("""
                func fail() -> Integer {
                    raise Exception(1, "x")
                }
                """);
    }

    @Test
    @DisplayName("a try statement always leaves when every clause that can run leaves")
    void try_termination_follows_its_clauses() {
        assertRejected("""
                func fail() -> Integer {
                    try {
                        raise Exception(1, "x")
                    } catch (e: Exception) {
                        raise e
                    }
                    TextWriter(Stdio.stdout()).writeLine(("unreachable") .. "")
                }
                """, TypeChecker.UNREACHABLE);
        // A finally that can complete normally leaves an exit through the try body.
        assertAccepted("""
                func fail() -> Integer {
                    try {
                        raise Exception(1, "x")
                    } finally {
                        TextWriter(Stdio.stdout()).writeLine(("cleanup") .. "")
                    }
                }
                """);
    }

    @Test
    @DisplayName("return is rejected inside finally")
    void return_is_rejected_in_finally() {
        assertRejected("""
                func f() -> Integer {
                    try {
                        return 1
                    } finally {
                        return 2
                    }
                }
                """, TypeChecker.FINALLY_RETURN);
    }

    @Test
    @DisplayName("break and continue are allowed inside finally")
    void loop_control_is_allowed_in_finally() {
        assertAccepted("""
                for item in Integer[2] {1, 2} {
                    try {
                        TextWriter(Stdio.stdout()).writeLine((item) .. "")
                    } finally {
                        if item == 1 {
                            continue
                        }
                        break
                    }
                }
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {"try", "catch", "finally", "raise"})
    @DisplayName("the new spellings are keywords, not identifiers")
    void the_new_spellings_are_keywords(String keyword) {
        assertRejected(keyword + " := 1\n", Parser.UNEXPECTED_TOKEN);
    }

    @Test
    @DisplayName("a clause written on the next line is reported once, with the layout rule")
    void clauses_must_share_the_closing_line() {
        Diagnostics result = diagnostics("""
                try {
                    TextWriter(Stdio.stdout()).writeLine(("x") .. "")
                }
                catch (e: Exception) {
                    TextWriter(Stdio.stdout()).writeLine(("y") .. "")
                }
                """);
        assertThat(result.all()).extracting(diagnostic -> diagnostic.code())
                .containsExactly(Parser.UNEXPECTED_TOKEN);
        assertThat(result.format("<t>")).contains("same line");
    }
}
