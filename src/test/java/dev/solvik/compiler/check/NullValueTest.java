package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import dev.solvik.compiler.testsupport.Checking;

/**
 * Every type is a reference type, so any variable may be set to {@code null} (§2, §3).
 *
 * <p>A variable keeps its type when its value is removed: reading it stays legal and yields
 * {@code null}, and only an operation that needs a value fails.
 */
@DisplayName("Type checker / null values")
class NullValueTest {

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
    @DisplayName("only a nullable declaration accepts Null; an ordinary type does not")
    void null_needs_a_nullable_type() {
        for (String type : new String[]{"Integer", "Double", "String", "Boolean", "Byte"}) {
            assertAccepted("mut value: " + type + "? = Null\nvalue = Null\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((value) .. \"\")\n");
            assertRejected("mut value: " + type + " = " + initial(type) + "\nvalue = Null\n");
        }
        assertAccepted("""
                mut items: Integer[2]? = Integer[2] {1, 2}
                items = Null
                """);
        assertAccepted("""
                type Point {
                    mut x := 0
                    pub func Point() { }
                }
                mut point: Point? = Point()
                point = Null
                """);
    }

    private static String initial(String type) {
        return switch (type) {
            case "Integer" -> "1";
            case "Double" -> "1.5";
            case "Boolean" -> "true";
            case "Byte" -> "Byte(7)";
            default -> "\"text\"";
        };
    }

    @Test
    @DisplayName("a nullable value keeps its static type, so assignment and comparison still work")
    void a_null_value_keeps_the_variables_type() {
        assertAccepted("""
                mut x: Integer? = 1
                x = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((x == Null) .. "")
                x = 2
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((x + 1) .. "")
                """);
        assertAccepted("""
                mut flag: Boolean? = true
                flag = Null
                flag = false
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((flag) .. "")
                """);
        assertAccepted("""
                mut text: String? = "value"
                text = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text) .. (" ") .. (text == Null))
                """);
    }

    @Test
    @DisplayName("an inferred nullable declaration infers T? and name? := Null infers Any?")
    void nullable_inference_follows_the_declaration_rules() {
        assertAccepted("""
                mut inferred? := "hello"
                inferred = Null
                inferred = "world"
                mut untyped? := Null
                untyped = "hello"
                untyped = 42
                nothing := Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((inferred) .. (" ") .. (untyped) .. (" ") .. (nothing))
                """);
        assertThat(check("mut untyped? := Null\nuntyped = 1\n").all())
                .extracting(d -> d.code()).isEmpty();
        assertRejected("""
                mut exact := Null
                exact = 1
                """);
    }

    @Test
    void null_may_fill_a_nullable_element_a_parameter_and_a_field() {
        assertAccepted("""
                mut items: Integer?[2] = Integer?[2] {1, Null}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((items[1] == Null) .. "")
                """);
        assertAccepted("""
                func show(value: Integer?) -> String {
                    return value .. ""
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((show(Null)) .. (" ") .. (show(1)))
                """);
        assertAccepted("""
                type Box {
                    value: Integer? = Null

                    pub func Box() {
                        self.value = 1
                    }

                    pub func report() -> String {
                        return self.value .. ""
                    }
                }
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Box().report()) .. "")
                """);
    }

    @Test
    void null_does_not_become_a_value_of_an_unrelated_type() {
        assertRejected("""
                mut x := 1
                x = "text"
                """);
        assertRejected("""
                mut items := Integer[2] {1, 2}
                items = String[1] {"a"}
                """);
        assertRejected("""
                x: Integer = Null
                """);
        assertRejected("""
                x := Integer[2] {1, Null}
                """);
    }

    @Test
    @DisplayName("a present value may be used, a nullable one must be proven present")
    void nullable_values_need_a_null_check() {
        assertRejected("""
                x: String? = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((x.size()) .. "")
                """);
        assertRejected("""
                mut n: Integer? = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((n + 1) .. "")
                """);
        assertRejected("""
                mut values: Integer[2]? = Null
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((values.size) .. "")
                """);
        assertRejected("""
                mut flag: Boolean? = Null
                if flag { }
                """);
        assertAccepted("""
                mut x: String? = Null
                if x != Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((x.size()) .. "")
                }
                """);
        assertAccepted("""
                mut x: String? = Null
                if x == Null {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine(("absent") .. "")
                } else {
                    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((x.size()) .. "")
                }
                """);
        assertAccepted("""
                mut n: Integer? = 1
                n = 2
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((n + 1) .. "")
                """);
    }
}
