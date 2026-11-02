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

/**
 * Failures the compiler can prove from literals are compile errors, not runtime aborts (§10).
 *
 * <p>Everything here would abort at run time for any input; catching it while transpiling means the
 * failure never reaches the program's execution at all.
 */
@DisplayName("Type checker / statically decidable failures")
class StaticFailureTest {

    private static Diagnostics check(String source) {
        Diagnostics diagnostics = new Diagnostics();
        Ast.Program program = new Parser(new Lexer(source, diagnostics).tokenize(), diagnostics)
                .parseProgram();
        Checking.check(program, diagnostics);
        return diagnostics;
    }

    private static void assertStaticFailure(String source) {
        assertThat(check(source).all())
                .describedAs("expected SOL-T008 for: %s", source)
                .extracting(Diagnostic::code)
                .contains(TypeChecker.STATIC_FAILURE);
    }

    private static void assertAccepted(String source) {
        Diagnostics result = check(source);
        assertThat(result.all())
                .describedAs("unexpected diagnostics: %s", result.format("<t>")).isEmpty();
    }

    @Test
    void division_and_remainder_by_a_literal_zero_are_compile_errors() {
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1 / 0) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1 % 0) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((BigDecimal(1.5) / BigDecimal(0)) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Integer(\"4\") / 0) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1 / -0) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((BigInteger(4) / BigInteger(0)) .. \"\")");
    }

    @Test
    @DisplayName("a BigDecimal division of literals that cannot terminate is a compile error")
    void non_terminating_literal_decimal_division_is_a_compile_error() {
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((BigDecimal(1.0) / BigDecimal(3)) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((BigDecimal(1) / BigDecimal(3)) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((BigDecimal(10) / BigDecimal(3)) .. \"\")");
    }

    @Test
    @DisplayName("binary floating-point division by zero follows IEEE 754 at run time")
    void floating_point_division_by_zero_is_not_a_compile_error() {
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1.5 / 0.0) .. \"\")");
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1.0 / 7) .. \"\")");
    }

    @Test
    void terminating_and_integral_literal_arithmetic_still_compiles() {
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1.5 / 2) .. \"\")");
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((1.5 / 3) .. \"\")"); // 0.5, which terminates
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((BigDecimal(10) / BigDecimal(2)) .. \"\")");
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((10 / 3) .. \"\")");
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((10 % 3) .. \"\")");
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((0 / 5) .. \"\")");
    }

    @Test
    @DisplayName("a divisor that is not a literal is still a run-time concern only")
    void a_computed_divisor_is_not_a_compile_error() {
        assertAccepted("""
                zero := 0
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((10 / zero) .. "")
                """);
    }

    @Test
    void literal_conversions_that_cannot_succeed_are_compile_errors() {
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Byte(1.5)) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Byte(\"300\")) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Byte(-129)) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Byte(1000)) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Integer(1.5)) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Integer(\"abc\")) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Integer(\"1.5\")) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Integer(true)) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Integer(null)) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((BigDecimal(\"x\")) .. \"\")");
        assertStaticFailure("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Boolean(\"yes\")) .. \"\")");
    }

    @Test
    void literal_conversions_that_succeed_still_compile() {
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Byte(127)) .. \"\")");
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Byte(\"-128\")) .. \"\")");
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Integer(\"42\")) .. \"\")");
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Integer(7)) .. \"\")");
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((BigDecimal(\"1.25\")) .. \"\")");
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((BigDecimal(1)) .. \"\")");
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Boolean(\"true\")) .. \"\")");
        assertAccepted("solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((String(42)) .. \"\")");
    }

    @Test
    @DisplayName("a non-literal conversion argument is left to run time")
    void conversions_of_computed_values_stay_runtime() {
        assertAccepted("""
                text := "42"
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Integer(text)) .. "")
                """);
        assertAccepted("""
                any := Any[1] {"x"}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((Integer(any[0])) .. "")
                """);
    }

    @Test
    void a_negative_literal_index_is_a_compile_error() {
        assertStaticFailure("""
                items := Integer[2] {1, 2}
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((items[-1]) .. "")
                """);
        assertStaticFailure("""
                items := Integer[2] {1, 2}
                items[-1] = 3
                """);
        assertAccepted("""
                items := Integer[2] {1, 2}
                index := 0
                solvik::io::TextWriter(solvik::io::Stdio.stdout()).writeLine((items[index]) .. "")
                """);
    }
}
