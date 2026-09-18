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
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Execution tests for the single Solvik equality rule (docs/LANGUAGE_SPEC.md section 3): built-in
 * scalars and enum values compare by value, ordinary class instances compare by identity, and the
 * same definition drives {@code Set} and {@code Map} membership. Every built-in scalar branch of
 * {@code SolvikValues.equal} is exercised directly so the rule cannot silently diverge from Java
 * {@code equals}.
 */
public final class SolvikEqualityTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "Byte(1) == Byte(1), true",
            "Byte(1) != Byte(2), true",
            "Short(1) == Short(1), true",
            "Short(1) != Short(2), true",
            "1 == 1, true",
            "1 != 2, true",
            "1L == 1L, true",
            "1L != 2L, true",
            "1.5f == 1.5f, true",
            "1.5f != 2.5f, true",
            "1.5 == 1.5, true",
            "1.5 != 2.5, true",
            "true == true, true",
            "true != false, true",
            "\"a\" == \"a\", true",
            "\"a\" != \"b\", true",
    })
    public void scalarEqualityComparesByValue(String expression, boolean expected) {
        assertThat(run("    println(" + expression + ")\n")).isEqualTo(expected + "\n");
    }

    @Test
    public void characterEqualityComparesByValue() {
        assertThat(run("""
                    println('A' == 'A')
                    println('A' != 'B')
                """)).isEqualTo("true\ntrue\n");
    }

    @Test
    public void anyOperandsCompareAcrossEveryRuntimeRepresentation() {
        assertThat(run("""
                    enum Color {
                        Red
                        Blue
                    }

                    func cmp(a: Any, b: Any): Boolean {
                        return a == b
                    }

                    println(cmp(1, 1))
                    println(cmp("x", "x"))
                    println(cmp(1, "x"))
                    println(cmp("x", 1))
                    println(cmp(true, true))
                    println(cmp(true, 1))
                    println(cmp(1L, 1L))
                    println(cmp(1.5, 1.5))
                    println(cmp(1.5f, 1.5f))
                    println(cmp(Color.Red, Color.Red))
                    println(cmp(Color.Red, Color.Blue))
                """)).isEqualTo("true\ntrue\nfalse\nfalse\ntrue\nfalse\ntrue\ntrue\ntrue\ntrue\nfalse\n");
    }

    @Test
    public void objectOperandsCompareByIdentityAndValue() {
        assertThat(run("""
                    class Box {
                    }

                    func cmp(a: Object, b: Object): Boolean {
                        return a == b
                    }

                    val first = Box()
                    val second = Box()
                    println(cmp(first, first))
                    println(cmp(first, second))
                    println(cmp(1, 1))
                    println(cmp("x", "x"))
                """)).isEqualTo("true\nfalse\ntrue\ntrue\n");
    }

    @Test
    public void ordinaryClassInstancesCompareByIdentity() {
        assertThat(run("""
                    class Box {
                    }

                    val a = Box()
                    val b = Box()
                    println(a == a)
                    println(a == b)
                    println(a != b)
                """)).isEqualTo("true\nfalse\ntrue\n");
    }

    @Test
    public void enumValuesCompareByValue() {
        assertThat(run("""
                    enum Color {
                        Red
                        Green
                    }

                    val red = Color.Red
                    val otherRed = Color.Red
                    val green = Color.Green
                    println(red == otherRed)
                    println(red == green)
                    println(red != green)
                """)).isEqualTo("true\nfalse\ntrue\n");
    }

    @Test
    public void enumValueWithPayloadComparesByValue() {
        assertThat(run("""
                    enum Result {
                        Ok(Int)
                        Error(String)
                    }

                    val first = Result.Ok(1)
                    val same = Result.Ok(1)
                    val different = Result.Ok(2)
                    println(first == same)
                    println(first == different)
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void setMembershipUsesScalarValueEquality() {
        assertThat(run("""
                    var longs: Set<Long> = Set(1L, 2L)
                    println(longs.contains(1L))
                    println(longs.contains(3L))
                    var doubles: Set<Double> = Set(1.5, 2.5)
                    println(doubles.contains(2.5))
                    var chars: Set<Char> = Set('a', 'b')
                    println(chars.contains('b'))
                """)).isEqualTo("true\nfalse\ntrue\ntrue\n");
    }

    @Test
    public void mapLookupUsesScalarValueEquality() {
        assertThat(run("""
                    var byLong: Map<Long, String> = Map(1L: "one")
                    println(byLong.containsKey(1L))
                    println(byLong.containsKey(2L))
                    var byDouble: Map<Double, Int> = Map(1.5: 1)
                    println(byDouble.containsKey(1.5))
                """)).isEqualTo("true\nfalse\ntrue\n");
    }

    @Test
    public void setMembershipUsesEnumValueEquality() {
        assertThat(run("""
                    enum Color {
                        Red
                        Green
                    }

                    var colors: Set<Color> = Set(Color.Red)
                    println(colors.contains(Color.Red))
                    println(colors.contains(Color.Green))
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void mapKeysUseEnumValueEquality() {
        assertThat(run("""
                    enum Color {
                        Red
                        Green
                    }

                    var names: Map<Color, String> = Map(Color.Red: "red")
                    println(names.containsKey(Color.Red))
                    println(names.containsKey(Color.Green))
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void setKeepsOnlyTheFirstOfTwoEqualValues() {
        assertThat(run("""
                    var values: Set<Long> = Set(1L, 1L, 2L)
                    println(values.size)
                """)).isEqualTo("2\n");
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
            return Source.newBuilder("solvik", source, "equality.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
