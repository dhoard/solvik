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

/**
 * End-to-end Phase 10 execution tests: safe member access and safe method calls short-circuit on
 * {@code null}, null coalescing chooses the right operand, {@code is} tests the runtime type,
 * {@code as} casts or raises a Solvik runtime type error, and null-check narrowing runs.
 */
public final class SolvikNullSafetyExecutionTest {

    private static final String BOX = """
            class Box {
                val value: Int

                Box(value: Int) {
                    this.value = value
                }
            }
            """;

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static PolyglotException runFailing(String source, ByteArrayOutputStream out) {
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source));
        } catch (PolyglotException e) {
            return e;
        }
        throw new AssertionError("program must fail at run time");
    }

    private static Source build(String source) {
        try {
            return Source.newBuilder("solvik", source, "null.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void safeAccessYieldsNullForANullReceiver() {
        assertThat(run(BOX + """
                    val a: Box? = null
                    val b: Box? = Box(7)
                    println(a == null)
                    println(b?.value ?? -1)
                """)).isEqualTo("true\n7\n");
    }

    @Test
    public void coalescingUsesTheFallbackForNull() {
        assertThat(run("""
                    val s: String? = null
                    val t: String? = "value"
                    println(s ?? "fallback")
                    println(t ?? "fallback")
                """)).isEqualTo("fallback\nvalue\n");
    }

    @Test
    public void coalescingDoesNotEvaluateTheFallbackWhenTheLeftIsNotNull() {
        assertThat(run("""
                func side(): String {
                    println("side")
                    return "fallback"
                }
                    val s: String? = "value"
                    println(s ?? side())
                """)).isEqualTo("value\n");
    }

    @Test
    public void safeMethodCallDoesNotEvaluateArgumentsForANullReceiver() {
        assertThat(run("""
                class Box {
                    func plus(x: Int): Int {
                        return x
                    }
                }
                func side(): Int {
                    println("side")
                    return 2
                }
                    val box: Box? = null
                    println(box?.plus(side()) ?? -1)
                """)).isEqualTo("-1\n");
    }

    @Test
    public void typeTestUsesTheRuntimeType() {
        assertThat(run("""
                class Box {
                }
                    val v: Any = Box()
                    println(v is Box)
                    println(v is String)
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void typeTestWorksForInterfaces() {
        assertThat(run("""
                interface Named {
                    func name(): String
                }
                class User implements Named {
                    func name(): String {
                        return "Doug"
                    }
                }
                    val v: Any = User()
                    println(v is Named)
                    val named = v as Named
                    println(named.name())
                """)).isEqualTo("true\nDoug\n");
    }

    @Test
    public void checkedCastReturnsTheValueOnSuccess() {
        assertThat(run(BOX + """
                    val v: Any = Box(3)
                    val box = v as Box
                    println(box.value)
                """)).isEqualTo("3\n");
    }

    @Test
    public void unsuccessfulCastRaisesARuntimeTypeError() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotException failure = runFailing(BOX + """
                    val v: Any = "not a box"
                    val box = v as Box
                    println(box.value)
                """, out);
        assertThat(failure).as("the cast must fail").isNotNull();
        assertThat(failure.isGuestException()).as("a guest exception is reported").isTrue();
        assertThat(failure.getMessage().contains("Box")).as("the message names the target type: " + failure.getMessage()).isTrue();
        assertThat(out.toString(StandardCharsets.UTF_8)).as("nothing is printed before the cast").isEqualTo("");
    }

    @Test
    public void nullCheckNarrowingExecutesTheNonNullBranch() {
        assertThat(run(BOX + """
                    val box: Box? = Box(5)
                    if (box != null) {
                        println(box.value)
                    }
                """)).isEqualTo("5\n");
    }

    @Test
    public void typeTestNarrowingExecutesTheNonNullBranch() {
        assertThat(run(BOX + """
                    val v: Any = Box(9)
                    if (v is Box) {
                        println(v.value)
                    }
                """)).isEqualTo("9\n");
    }

    @Test
    public void builtinTypeTestsAndCastsExecute() {
        assertThat(run("""
                    val v: Any = 1
                    println(v is Int)
                    println(v is String)
                    val n = v as Int
                    println(n + 1)
                """)).isEqualTo("true\nfalse\n2\n");
    }

    @Test
    public void inheritanceTypeTestsWalkTheRuntimeClassChain() {
        assertThat(run("""
                open class Animal {
                    open func speak(): String {
                        return "..."
                    }
                }
                class Dog extends Animal {
                    override func speak(): String {
                        return "woof"
                    }
                }
                    val v: Any = Dog()
                    println(v is Dog)
                    println(v is Animal)
                    val animal = v as Animal
                    println(animal.speak())
                """)).isEqualTo("true\ntrue\nwoof\n");
    }
}
