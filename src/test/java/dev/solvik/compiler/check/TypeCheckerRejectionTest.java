package dev.solvik.compiler.check;

import static org.assertj.core.api.Assertions.assertThat;

import dev.solvik.compiler.ast.Ast;
import dev.solvik.compiler.diagnostic.Diagnostics;
import dev.solvik.compiler.lexer.Lexer;
import dev.solvik.compiler.parser.Parser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import dev.solvik.compiler.testsupport.Checking;

/** Covers the checker's rejection branches, one rule per case. */
@DisplayName("Type checker rejections")
class TypeCheckerRejectionTest {

    private static List<String> codes(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        return diagnostics.all().stream().map(d -> d.code()).toList();
    }

    private static void assertRejected(String source) {
        assertThat(codes(source)).contains(TypeChecker.TYPE_ERROR);
    }

    private static void assertAccepted(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        assertThat(diagnostics.all())
                .describedAs("unexpected diagnostics for: %s", source).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mut a := -true",
            "mut a := !1",
            "mut a := \"a\" < 1",
            "mut a := \"a\" == 1",
            "mut a := true + 1",
            "x := 1\ny := x[0]",
            "mut a := Byte[1] {\"x\"}",
            "mut a := Byte[\"2\"] {1, 2}",
            "if 1 { }",
            "while false { }\nfor x in 1 { }",
            "func f() -> Integer {\n    return \"x\"\n}",
            "func f() {\n}\nfunc f() {\n}",
            "func f(a: Integer) {\n}\nf(\"x\")",
            "mut a := unknownName",
            "mut a := 1\nb := a.equals(1).size()",
    })
    @DisplayName("rejects programs that break a settled rule")
    void rejects(String source) {
        assertRejected(source);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mut a := -Byte(1)",
            "mut a := -1.5",
            "mut a := !true",
            "mut a := 1 < 2",
            "mut a := 1 == 1.0",
            "mut a := 1 === 1",
            "mut a := Byte(1) < Byte(2)",
            "bytes := Byte[2] {1, 2}\nx := bytes[Byte(1)]",
            "mut a := 1\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.hashCode()) .. \"\")",
            "mut a := 1\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.toString()) .. \"\")",
            "mut a := 1\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.type()) .. \"\")",
            "mut a := 1\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.equals(2)) .. \"\")",
            "bytes := Byte[2] {1, 2}\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((bytes.size) .. \"\")",
            "func f(a: Any) -> String {\n    return a.type()\n}\ns := f(1)",
            "mut a: Integer? = Null\nif true {\n    a = 1\n}",
            "nested := 1\nif true {\n    inner := 2\n    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((inner) .. \"\")\n}",
            "mut a := 1\nmut b := a\nb = 2",
    })
    @DisplayName("accepts programs that follow the rules")
    void accepts(String source) {
        assertAccepted(source);
    }

    @Test
    void rejects_unary_on_void_and_method_arity_errors() {
        assertRejected("func note() {\n}\nx := -note()");
        assertRejected("mut a := 1\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.size(1)) .. \"\")");
        assertRejected("bytes := Byte[1] {1}\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((bytes.size(1)) .. \"\")");
        assertRejected("mut a := 1\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.equals()) .. \"\")");
        assertRejected("mut a := 1\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.equals(1, 2)) .. \"\")");
    }

    @Test
    void rejects_unknown_and_unsupported_constructs() {
        assertThat(codes("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((nope()) .. \"\")")).contains(TypeChecker.TYPE_ERROR);
        assertThat(codes("mut a := 1\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.field) .. \"\")")).contains(TypeChecker.TYPE_ERROR);
        assertThat(codes("mut a := 1\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((a.nope()) .. \"\")")).contains(TypeChecker.TYPE_ERROR);
        assertThat(codes("type Point { }")).isEmpty(); // declaring a type is now supported
        assertThat(codes("mut a := name[1]")).contains(TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("an array's size is a read-only member, never a function")
    void rejects_the_array_size_call_form_and_writes_to_the_member() {
        assertThat(codes("values := Integer[2] {1, 2}\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((values.size()) .. \"\")"))
                .containsExactly(TypeChecker.TYPE_ERROR);
        assertThat(codes("values := Integer[2] {1, 2}\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((values.size(1)) .. \"\")"))
                .contains(TypeChecker.TYPE_ERROR);
        assertThat(codes("values := Integer[2] {1, 2}\nvalues.size = 3"))
                .containsExactly(TypeChecker.IMMUTABLE_MEMBER);
        assertThat(codes("values := Integer[2] {1, 2}\nvalues.size ..= \"x\""))
                .containsExactly(TypeChecker.IMMUTABLE_MEMBER);
        assertThat(codes("values := Integer[2] {1, 2}\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((values.length) .. \"\")"))
                .containsExactly(TypeChecker.TYPE_ERROR);
        assertThat(codes("text := \"abc\"\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((text.size) .. \"\")"))
                .containsExactly(TypeChecker.TYPE_ERROR);
        assertAccepted("values := Integer[2] {1, 2}\nsolvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((values.size) .. \"\")");
    }

    @Test
    void rejects_assigning_to_something_that_is_not_a_variable() {
        assertThat(codes("mut a := 1\na.size() = 2")).contains(TypeChecker.UNSUPPORTED);
    }

    @Test
    void rejects_void_used_as_a_value_and_conversion_arity() {
        assertRejected("func note() {\n}\nx := note()");
        assertRejected("mut a := Integer(1, 2)");
        assertThat(codes("mut a := 1\nreturn a"))
                .contains(TypeChecker.RETURN_OUTSIDE_FUNCTION);
    }

    @Test
    void rejects_a_parameter_type_mismatch_and_wrong_argument_counts() {
        assertRejected("""
                func take(n: Integer) -> Integer {
                    return n
                }
                mut a := take(1.5)
                """);
        assertRejected("""
                func take(n: Integer) -> Integer {
                    return n
                }
                mut a := take(1, 2)
                """);
    }

    @Test
    void rejects_for_in_over_a_value_that_is_not_an_array() {
        assertRejected("for x in 1 {\n    solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((x) .. \"\")\n}");
    }

    @Test
    void a_void_function_returning_a_value_and_a_valued_function_returning_nothing() {
        assertRejected("func f() {\n    return 1\n}");
        assertRejected("func f() -> Integer {\n    return\n}");
    }
}
