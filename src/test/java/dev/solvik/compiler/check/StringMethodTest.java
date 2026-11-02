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

/** String methods (§5, "String operations"). */
@DisplayName("Type checker / string methods")
class StringMethodTest {

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
    void every_string_method_is_accepted_with_its_result_type() {
        assertAccepted("""
                text := " a,b "
                size := text.size()
                stripped := text.strip()
                left := text.stripLeading()
                right := text.stripTrailing()
                upper := text.toUpperCase()
                lower := text.toLowerCase()
                part := text.substring(1)
                range := text.substring(1, 3)
                at := text.indexOf("b")
                has := text.contains("b")
                starts := text.startsWith(" ")
                ends := text.endsWith(" ")
                replaced := text.replace("a", "z")
                parts := text.split(",")
                TextWriter(Stdio.stdout()).writeLine((size) .. (stripped) .. (left) .. (right) .. (upper) .. (lower) .. (part) .. (range) .. (at) .. (has) .. (starts) .. (ends) .. (replaced) .. (parts.size))
                """);
    }

    @Test
    void a_split_result_is_an_array_of_strings() {
        assertAccepted("""
                parts := "a,b".split(",")
                for part in parts {
                    TextWriter(Stdio.stdout()).writeLine((part.toUpperCase()) .. "")
                }
                first := parts[0]
                TextWriter(Stdio.stdout()).writeLine((first.size()) .. "")
                parts[0] = "changed"
                """);
    }

    @Test
    void string_methods_chain() {
        assertAccepted("""
                TextWriter(Stdio.stdout()).writeLine((" a b ".strip().split(" ")[1].toUpperCase()) .. "")
                """);
    }

    @Test
    void wrong_arguments_are_rejected() {
        assertRejected("""
                text := "abc"
                TextWriter(Stdio.stdout()).writeLine((text.substring()) .. "")
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                text := "abc"
                TextWriter(Stdio.stdout()).writeLine((text.substring(1, 2, 3)) .. "")
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                text := "abc"
                TextWriter(Stdio.stdout()).writeLine((text.substring("1")) .. "")
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                text := "abc"
                TextWriter(Stdio.stdout()).writeLine((text.contains(1)) .. "")
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                text := "abc"
                TextWriter(Stdio.stdout()).writeLine((text.replace("a")) .. "")
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                text := "abc"
                TextWriter(Stdio.stdout()).writeLine((text.strip(1)) .. "")
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                text := "abc"
                TextWriter(Stdio.stdout()).writeLine((text.unknownMethod()) .. "")
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("string methods need a String receiver")
    void a_non_string_receiver_is_rejected() {
        assertRejected("""
                count := 1
                TextWriter(Stdio.stdout()).writeLine((count.strip()) .. "")
                """, TypeChecker.TYPE_ERROR);
        assertRejected("""
                items := String[1] {"a"}
                TextWriter(Stdio.stdout()).writeLine((items.split(",")) .. "")
                """, TypeChecker.TYPE_ERROR);
    }

    @Test
    @DisplayName("a literal substring or split that cannot work is a compile error")
    void literal_text_failures_are_compile_errors() {
        assertRejected("TextWriter(Stdio.stdout()).writeLine((\"abc\".substring(5)) .. \"\")", TypeChecker.STATIC_FAILURE);
        assertRejected("TextWriter(Stdio.stdout()).writeLine((\"abc\".substring(2, 1)) .. \"\")", TypeChecker.STATIC_FAILURE);
        assertRejected("TextWriter(Stdio.stdout()).writeLine((\"abc\".substring(-1)) .. \"\")", TypeChecker.STATIC_FAILURE);
        assertRejected("TextWriter(Stdio.stdout()).writeLine((\"abc\".substring(0, 9)) .. \"\")", TypeChecker.STATIC_FAILURE);
        assertRejected("TextWriter(Stdio.stdout()).writeLine((\"abc\".split(\"\")) .. \"\")", TypeChecker.STATIC_FAILURE);
    }

    @Test
    @DisplayName("indices at the boundaries are accepted")
    void boundary_indices_are_accepted() {
        assertAccepted("TextWriter(Stdio.stdout()).writeLine((\"abc\".substring(0)) .. \"\")");
        assertAccepted("TextWriter(Stdio.stdout()).writeLine((\"abc\".substring(3)) .. \"\")");
        assertAccepted("TextWriter(Stdio.stdout()).writeLine((\"abc\".substring(0, 0)) .. \"\")");
        assertAccepted("TextWriter(Stdio.stdout()).writeLine((\"abc\".substring(0, 3)) .. \"\")");
    }

    @Test
    @DisplayName("a computed index or separator is checked when the program runs")
    void computed_arguments_stay_runtime() {
        assertAccepted("""
                text := "abc"
                index := 9
                TextWriter(Stdio.stdout()).writeLine((text.substring(index)) .. "")
                """);
        assertAccepted("""
                text := "abc"
                separator := ""
                TextWriter(Stdio.stdout()).writeLine((text.split(separator)) .. "")
                """);
    }
}
