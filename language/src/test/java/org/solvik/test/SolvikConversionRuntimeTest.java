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
 * Execution tests for explicit numeric conversions (docs/LANGUAGE_SPEC.md section 4). Every target
 * and source representation is exercised, an integral target rejects an out-of-range value with a
 * Solvik arithmetic error, an out-of-range constant conversion is rejected at compile time, and
 * floating-point targets follow IEEE 754 without range checking. There is no implicit widening or
 * narrowing, so a conversion is always written.
 */
public final class SolvikConversionRuntimeTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "Byte(7), 7",
            "Short(7), 7",
            "Integer(7L), 7",
            "Long(7), 7",
            "Float(7), 7.0",
            "Double(7), 7.0",
            "Byte(Short(7)), 7",
            "Short(Byte(7)), 7",
            "Long(Integer(7)), 7",
            "Integer(Long(7L)), 7",
            "Double(1.5f), 1.5",
            "Float(1.5), 1.5",
            "Double(Byte(7)), 7.0",
            "Float(Short(7)), 7.0",
    })
    public void conversionsProduceTheirTargetValue(String expression, String expected) {
        assertThat(run("    println(" + expression + ")\n")).isEqualTo(expected + "\n");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "Integer(2.9), 2",
            "Integer(-2.9), -2",
            "Long(1.9), 1",
            "Long(-1.9), -1",
            "Byte(1.9), 1",
            "Short(1.9), 1",
            "Long(Float(1.25f)), 1",
            "Integer(Float(2.75f)), 2",
    })
    public void floatingToIntegralConversionTruncatesTowardZero(String expression, String expected) {
        assertThat(run("    println(" + expression + ")\n")).isEqualTo(expected + "\n");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "Byte(127), 127",
            "Byte(-128), -128",
            "Short(32767), 32767",
            "Short(-32768), -32768",
            "Integer(2147483647L), 2147483647",
            "Integer(-2147483648L), -2147483648",
            "Long(2147483647), 2147483647",
    })
    public void integralConversionAcceptsItsBoundaries(String expression, String expected) {
        assertThat(run("    println(" + expression + ")\n")).isEqualTo(expected + "\n");
    }

    @ParameterizedTest(name = "{0} is a compile-time error")
    @ValueSource(strings = {
            "Byte(128)",
            "Short(32768)",
            "Integer(2147483648L)",
            "Byte(300.0)",
    })
    public void outOfRangeConstantConversionIsRejectedAtCompileTime(String expression) {
        PolyglotException failure = failureOf("    println(" + expression + ")\n");
        assertThat(failure.isSyntaxError()).as(failure.getMessage()).isTrue();
        assertThat(failure.getMessage()).contains("SOLV-TYPE-021");
    }

    @ParameterizedTest(name = "{1} out of range")
    @CsvSource({
            "var x = 300, Byte(x)",
            "var x = 32768, Short(x)",
            "var x = 2147483648L, Integer(x)",
    })
    public void outOfRangeConversionOfAValueIsARuntimeError(String declaration, String expression) {
        PolyglotException failure = failureOf("    " + declaration + "\n    println(" + expression + ")\n");
        assertThat(failure.isSyntaxError()).as(failure.getMessage()).isFalse();
        assertThat(failure.getMessage()).contains("out of range");
    }

    @Test
    public void nanAndInfinityCannotConvertToAnIntegralType() {
        assertThat(failureOf("    var d = 0.0 / 0.0\n    println(Integer(d))\n").getMessage()).contains("out of range");
        assertThat(failureOf("    var d = 1.0 / 0.0\n    println(Long(d))\n").getMessage()).contains("out of range");
        assertThat(failureOf("    var f = 1.0f / 0.0f\n    println(Byte(f))\n").getMessage()).contains("out of range");
    }

    @Test
    public void floatingTargetsDoNotRangeCheckTheirIntegralSources() {
        assertThat(run("""
                    println(Float(9223372036854775807L))
                    println(Double(9223372036854775807L))
                """)).isEqualTo("9.223372E18\n9.223372036854776E18\n");
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
            return Source.newBuilder("solvik", source, "conversion.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
