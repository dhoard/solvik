/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.solvik.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Execution tests for arithmetic, ordering, and negation on the non-{@code Int} numeric types
 * ({@code Byte}, {@code Short}, {@code Long}, {@code Float}, {@code Double}). Static analysis fixes
 * both operand types, so the lowered runtime dispatches on the runtime representation; these tests
 * drive every representation through the same operator surface that the {@code Int} fast path
 * already covers (docs/LANGUAGE_SPEC.md section 4). Integral overflow and division by zero must
 * raise a Solvik arithmetic error, while floating-point arithmetic follows IEEE 754.
 */
public final class SolvikNumericRuntimeTest {

    @ParameterizedTest(name = "{0} arithmetic")
    @CsvSource({
            "Byte(7) + Byte(2), 9",
            "Byte(7) - Byte(2), 5",
            "Byte(7) * Byte(2), 14",
            "Byte(7) / Byte(2), 3",
            "Short(7) + Short(2), 9",
            "Short(7) - Short(2), 5",
            "Short(7) * Short(2), 14",
            "Short(7) / Short(2), 3",
            "7L + 2L, 9",
            "7L - 2L, 5",
            "7L * 2L, 14",
            "7L / 2L, 3",
    })
    public void integralArithmeticExecutes(String expression, String expected) {
        assertThat(run("    println(" + expression + ")\n")).isEqualTo(expected + "\n");
    }

    @ParameterizedTest(name = "{0} arithmetic")
    @CsvSource({
            "7.5f + 2.5f, 10.0",
            "7.5f - 2.5f, 5.0",
            "7.5f * 2.0f, 15.0",
            "7.5f / 2.5f, 3.0",
            "7.5 + 2.5, 10.0",
            "7.5 - 2.5, 5.0",
            "7.5 * 2.0, 15.0",
            "7.5 / 2.5, 3.0",
    })
    public void floatingArithmeticExecutes(String expression, String expected) {
        assertThat(run("    println(" + expression + ")\n")).isEqualTo(expected + "\n");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "Byte(1) < Byte(2), true",
            "Byte(2) <= Byte(2), true",
            "Byte(3) > Byte(2), true",
            "Byte(1) >= Byte(3), false",
            "Short(1) < Short(2), true",
            "Short(2) <= Short(2), true",
            "Short(3) > Short(2), true",
            "Short(1) >= Short(3), false",
            "1L < 2L, true",
            "2L <= 2L, true",
            "3L > 2L, true",
            "1L >= 3L, false",
            "1.5f < 2.5f, true",
            "2.5f <= 2.5f, true",
            "3.5f > 2.5f, true",
            "1.5f >= 3.5f, false",
            "1.5 < 2.5, true",
            "2.5 <= 2.5, true",
            "3.5 > 2.5, true",
            "1.5 >= 3.5, false",
    })
    public void orderingComparisonExecutesForEveryNumericType(String expression, boolean expected) {
        assertThat(run("    println(" + expression + ")\n")).isEqualTo(expected + "\n");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "Byte(-5), -5",
            "Short(-5), -5",
            "-7L, -7",
            "-1.5f, -1.5",
            "-1.5, -1.5",
    })
    public void unaryNegationExecutesForEveryNumericType(String expression, String expected) {
        assertThat(run("    println(" + expression + ")\n")).isEqualTo(expected + "\n");
    }

    @Test
    public void byteMinValueIsNegatedOnlyWhenRepresentable() {
        assertThat(run("    println(-Byte(127))\n")).isEqualTo("-127\n");
        assertThat(failureOf("    println(-Byte(-128))\n").getMessage()).contains("overflow");
    }

    @Test
    public void shortMinValueIsNegatedOnlyWhenRepresentable() {
        assertThat(run("    println(-Short(32767))\n")).isEqualTo("-32767\n");
        assertThat(failureOf("    println(-Short(-32768))\n").getMessage()).contains("overflow");
    }

    @Test
    public void longMinValueNegationOverflows() {
        String minValue = "(Long(-9223372036854775807L) - 1L)";
        assertThat(failureOf("    println(-" + minValue + ")\n").getMessage()).contains("overflow");
    }

    @ParameterizedTest(name = "{0} overflow")
    @ValueSource(strings = {
            "Byte(127) + Byte(1)",
            "Byte(-128) - Byte(1)",
            "Byte(100) * Byte(2)",
            "Short(32767) + Short(1)",
            "Short(-32768) - Short(1)",
            "Short(200) * Short(200)",
            "9223372036854775807L + 1L",
            "Long(-9223372036854775807L) - 2L",
            "3037000500L * 3037000500L",
    })
    public void integralOverflowRaisesAnArithmeticError(String expression) {
        PolyglotException failure = failureOf("    println(" + expression + ")\n");
        assertThat(failure.isSyntaxError()).as(failure.getMessage()).isFalse();
        assertThat(failure.getMessage()).contains("overflow");
    }

    @ParameterizedTest(name = "{0} division by zero")
    @ValueSource(strings = {
            "Byte(1) / Byte(0)",
            "Short(1) / Short(0)",
            "1L / 0L",
    })
    public void integralDivisionByZeroRaisesAnArithmeticError(String expression) {
        PolyglotException failure = failureOf("    println(" + expression + ")\n");
        assertThat(failure.isSyntaxError()).as(failure.getMessage()).isFalse();
        assertThat(failure.getMessage()).contains("division by zero");
    }

    @ParameterizedTest(name = "{0} min divided by -1")
    @ValueSource(strings = {
            "Byte(-128) / Byte(-1)",
            "Short(-32768) / Short(-1)",
            "(-2147483647 - 1) / -1",
            "(Long(-9223372036854775807L) - 1L) / -1L",
    })
    public void dividingAnIntegralMinimumByNegativeOneOverflows(String expression) {
        PolyglotException failure = failureOf("    println(" + expression + ")\n");
        assertThat(failure.isSyntaxError()).as(failure.getMessage()).isFalse();
        assertThat(failure.getMessage()).contains("overflow");
    }

    @Test
    public void floatingPointDivisionByZeroFollowsIeee754() {
        assertThat(run("""
                    println(1.0 / 0.0)
                    println(-1.0 / 0.0)
                    println(0.0 / 0.0)
                    println(1.0f / 0.0f)
                """)).isEqualTo("Infinity\n-Infinity\nNaN\nInfinity\n");
    }

    @Test
    public void floatingPointNegationPreservesNonFiniteValues() {
        assertThat(run("""
                    println(-(1.0 / 0.0))
                    println(-(0.0 / 0.0))
                """)).isEqualTo("-Infinity\nNaN\n");
    }

    @Test
    public void nanIsUnorderedAndNeverEqualsItself() {
        assertThat(run("""
                    val nan = 0.0 / 0.0
                    println(nan < 1.0)
                    println(nan > 1.0)
                    println(nan <= 1.0)
                    println(nan >= 1.0)
                    println(nan == nan)
                    println(nan != nan)
                """)).isEqualTo("false\nfalse\nfalse\nfalse\nfalse\ntrue\n");
    }

    @Test
    public void integralArithmeticRejectsNonRepresentableIntermediateResults() {
        assertThat(run("""
                    println(Byte(100) + Byte(27))
                    println(Short(30000) + Short(767))
                """)).isEqualTo("127\n30767\n");
    }

    private static PolyglotException failureOf(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source));
        } catch (PolyglotException e) {
            return e;
        }
        throw new AssertionError("expected a failure but the program completed: " + source);
    }

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Source build(String source) {
        try {
            return Source.newBuilder("solvik", source, "numeric-runtime.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
