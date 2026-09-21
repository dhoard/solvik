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

/**
 * Parameter passing for every built-in parameter representation (docs/LANGUAGE_SPEC.md section 6).
 * Source arguments arrive as an {@code Object[]} and are copied into typed frame slots, so a
 * function parameter of each numeric type, {@code Boolean}, {@code Character}, {@code String}, and a
 * user class must receive and return its value unchanged through a real call.
 */
public final class SolvikFunctionParameterTest {

    @Test
    public void everyParameterRepresentationRoundTripsThroughACall() {
        assertThat(run("""
                    class Point {
                        val x: Integer

                        Point(x: Integer) {
                            this.x = x
                        }
                    }

                    func addByte(a: Byte, b: Byte): Byte {
                        return a + b
                    }

                    func addShort(a: Short, b: Short): Short {
                        return a + b
                    }

                    func addInt(a: Integer, b: Integer): Integer {
                        return a + b
                    }

                    func addLong(a: Long, b: Long): Long {
                        return a + b
                    }

                    func addFloat(a: Float, b: Float): Float {
                        return a + b
                    }

                    func addDouble(a: Double, b: Double): Double {
                        return a + b
                    }

                    func flip(value: Boolean): Boolean {
                        return !value
                    }

                    func decorate(value: Character): String {
                        return value .. "!"
                    }

                    func greet(name: String): String {
                        return "hi " .. name
                    }

                    func xOf(point: Point): Integer {
                        return point.x
                    }

                    println(addByte(Byte(1), Byte(2)))
                    println(addShort(Short(1), Short(2)))
                    println(addInt(1, 2))
                    println(addLong(1L, 2L))
                    println(addFloat(1.5f, 2.5f))
                    println(addDouble(1.5, 2.5))
                    println(flip(true))
                    println(decorate('a'))
                    println(greet("x"))
                    println(xOf(Point(7)))
                """)).isEqualTo("3\n3\n3\n3\n4.0\n4.0\nfalse\na!\nhi x\n7\n");
    }

    @Test
    public void unitReturningFunctionRunsItsSideEffects() {
        assertThat(run("""
                    func bump() {
                        println("bump")
                    }

                    func explicitUnit(): Unit {
                        println("unit")
                    }

                    bump()
                    bump()
                    explicitUnit()
                """)).isEqualTo("bump\nbump\nunit\n");
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
            return Source.newBuilder("solvik", source, "parameters.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
