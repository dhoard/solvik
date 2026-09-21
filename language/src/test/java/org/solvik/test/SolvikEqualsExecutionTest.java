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
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

/**
 * Positive execution tests for the explicit {@code equals(other: Any?): Boolean} universal member
 * (docs/LANGUAGE_SPEC.md section 3). These isolate the explicit {@code .equals()} call syntax from
 * the mixed {@code ==}/{@code !==} operators. The member is reachable on every non-null value; it
 * stays behaviorally aligned with {@code ==}; the same recursive and reference-identity rules apply
 * to class instances and enum payloads; and guest exceptions from an overriding member propagate.
 * Exact stdout is asserted.
 *
 * <p>Receivers are deliberately non-null: the member is only callable on non-null values, so every
 * value here is a non-null literal or a constructor result. Nullable {@code val} bindings of class,
 * enum, and collection types are excluded because the member call on them is a compile-time error
 * (SOLV-TYPE-024), which is covered by the negative tests.
 */
public final class SolvikEqualsExecutionTest {

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source));
        }
        return out.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Source build(String source) {
        try {
            return Source.newBuilder("solvik", source, "equals.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void equalsMemberMatchesEqualsOperatorForNonNullScalars() {
        // Non-null literals and Byte/Short constructor results all reach the built-in member and stay
        // aligned with ==.
        assertThat(run("""
                        println(1.equals(1))
                        println(1.equals(2))
                        println(1L.equals(1L))
                        println(1.5.equals(1.5))
                        println('a'.equals('a'))
                        println("x".equals("x"))
                        println(true.equals(true))
                        println(Byte(1).equals(Byte(1)))
                        println(Short(-3).equals(Short(-3)))
                        func noop() {}
                        println(noop().equals(noop()))
                        """)).isEqualTo("true\nfalse\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\n");
    }

    @Test
    public void equalsMemberIsReferenceIdentityForClassInstances() {
        // A user class with no equals override uses the root default (reference identity).
        assertThat(run("""
                        class Box {
                        }
                        val same = Box()
                        val other = Box()
                        println(same.equals(same))
                        println(same.equals(other))
                        """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void equalsMemberRecursesThroughEnumValues() {
        // Recursive enum equality: same variant+payload is equal, a different payload is not.
        assertThat(run("""
                        enum Color {
                            Red
                            Green
                        }
                        enum Pair {
                            P(Color)
                        }
                        println(Pair.P(Color.Red).equals(Pair.P(Color.Red)))
                        println(Pair.P(Color.Red).equals(Pair.P(Color.Green)))
                        """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void equalsMemberComparesIntegerLiteralsByValue() {
        // Integer literals are non-null and compare by value through the member.
        assertThat(run("""
                        println(1.equals(1))
                        println(1.equals(2))
                        println(100.equals(100))
                        """)).isEqualTo("true\nfalse\ntrue\n");
    }

    @Test
    public void equalsMemberFailureFromOverridePropagates() {
        Throwable thrown = catchThrowable(() -> run("""
                        class Bad {
                            override func equals(other: Any?): Boolean {
                                return 1 / 0 == 0
                            }
                        }
                        val a = Bad()
                        val b = Bad()
                        println(a.equals(b))
                        """));
        assertThat(thrown).isNotNull();
    }
}
