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
import static org.solvik.test.SolvikTestSupport.expectThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

/**
 * End-to-end Phase 11 execution tests (docs/LANGUAGE_SPEC.md section 11): generic construction,
 * generic functions and methods, and generic interface dispatch run through the Truffle AST backend
 * with erased runtime representation.
 */
public final class SolvikGenericsExecutionTest {

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source, "test.sol"));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Source build(String source, String name) {
        try {
            return Source.newBuilder("solvik", source, name).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void genericConstructionAndMembersExecute() {
        assertThat(run("""
                class Box<T> {
                    var value: T

                    Box(value: T) {
                        this.value = value
                    }

                    func get(): T {
                        return this.value
                    }
                }

                    val intBox = Box(5)
                    println(intBox.value)
                    println(intBox.get())
                    val stringBox = Box("hi")
                    println(stringBox.get())
                """)).isEqualTo("5\n5\nhi\n");
    }

    @Test
    public void genericFunctionExecutes() {
        assertThat(run("""
                func identity<T>(x: T): T {
                    return x
                }

                    println(identity(7))
                    println(identity("hello"))
                """)).isEqualTo("7\nhello\n");
    }

    @Test
    public void genericMethodExecutes() {
        assertThat(run("""
                class Box<T> {
                    var value: T

                    Box(value: T) {
                        this.value = value
                    }

                    func replaceWith<U>(value: U): U {
                        return value
                    }
                }

                    val box = Box(1)
                    println(box.replaceWith("hello"))
                    println(box.value)
                """)).isEqualTo("hello\n1\n");
    }

    @Test
    public void genericInterfaceDispatchExecutes() {
        assertThat(run("""
                interface Container<T> {
                    func get(): T
                }

                class StringBox implements Container<String> {
                    func get(): String {
                        return "boxed"
                    }
                }

                func describe(container: Container<String>): String {
                    return container.get()
                }

                    println(describe(StringBox()))
                """)).isEqualTo("boxed\n");
    }

    @Test
    public void genericClassCanImplementAMatchingGenericInterface() {
        assertThat(run("""
                interface Container<T> {
                    func get(): T
                }

                class Holder<T> implements Container<T> {
                    var value: T

                    Holder(value: T) {
                        this.value = value
                    }

                    func get(): T {
                        return this.value
                    }
                }

                    val holder = Holder("value")
                    val container: Container<String> = holder
                    println(container.get())
                """)).isEqualTo("value\n");
    }

    @Test
    public void inheritedGenericMembersExecuteThroughASubclass() {
        assertThat(run("""
                open class Box<T> {
                    var value: T

                    Box(value: T) {
                        this.value = value
                    }

                    open func get(): T {
                        return this.value
                    }
                }

                class IntBox extends Box<Integer> {
                    IntBox(value: Integer) {
                        super(value)
                    }
                }

                    val box = IntBox(9)
                    println(box.value)
                    println(box.get())
                """)).isEqualTo("9\n9\n");
    }

    @Test
    public void genericClassDisplaysAsItsClassName() {
        assertThat(run("""
                class Box<T> {
                    var value: T

                    Box(value: T) {
                        this.value = value
                    }
                }

                    println(Box(5))
                """)).isEqualTo("Box\n");
    }

    @Test
    public void parameterizedArgumentInfersTheTypeArgument() {
        assertThat(run("""
                class Box<T> {
                    var value: T

                    Box(value: T) {
                        this.value = value
                    }
                }

                func unwrap<T>(box: Box<T>): T {
                    return box.value
                }

                    val ints: Box<Integer> = Box(7)
                    val texts: Box<String> = Box("hi")
                    println(unwrap(ints))
                    println(unwrap(texts))
                    println(unwrap(Box(3)))
                """)).isEqualTo("7\nhi\n3\n");
    }

    @Test
    public void nullableParameterInfersFromTheNonNullArgument() {
        assertThat(run("""
                func choose<T>(value: T?, fallback: T): T {
                    if (value == null) {
                        return fallback
                    }
                    return value
                }

                    println(choose(9, 0))
                    println(choose("picked", "fallback"))
                """)).isEqualTo("9\npicked\n");
    }

    @Test
    public void enclosingTypeParameterInAParameterDoesNotConstrainInference() {
        assertThat(run("""
                class Box<T> {
                    var value: T

                    Box(value: T) {
                        this.value = value
                    }

                    func firstOf<U>(other: Box<T>, fallback: U): U {
                        println(this.value)
                        return fallback
                    }
                }

                    val box: Box<Integer> = Box(1)
                    val other: Box<Integer> = Box(2)
                    println(box.firstOf(other, "done"))
                """)).isEqualTo("1\ndone\n");
    }

    @Test
    public void genericInvarianceErrorSuppressesAllOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            PolyglotException failure = expectThrows(PolyglotException.class, () -> context.eval(build("""
                    class Box<T> {
                        var value: T

                        Box(value: T) {
                            this.value = value
                        }
                    }

                        println("before")
                        val box: Box<Integer> = Box("x")
                        println("after")
                    """, "test.sol")));
            assertThat(out.size()).isEqualTo(0);
            assertThat(failure.isGuestException() || failure.isSyntaxError() || failure.isInternalError()).isEqualTo(true);
        }
    }
}
