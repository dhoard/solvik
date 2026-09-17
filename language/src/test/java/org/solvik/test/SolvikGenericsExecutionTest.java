/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Test;

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
        assertEquals("5\n5\nhi\n", run("""
                class Box<T> {
                    var value: T

                    init(value: T) {
                        this.value = value
                    }

                    func get(): T {
                        return this.value
                    }
                }

                func main(): Unit {
                    val intBox = Box(5)
                    println(intBox.value)
                    println(intBox.get())
                    val stringBox = Box("hi")
                    println(stringBox.get())
                }
                """));
    }

    @Test
    public void genericFunctionExecutes() {
        assertEquals("7\nhello\n", run("""
                func identity<T>(x: T): T {
                    return x
                }

                func main(): Unit {
                    println(identity(7))
                    println(identity("hello"))
                }
                """));
    }

    @Test
    public void genericMethodExecutes() {
        assertEquals("hello\n1\n", run("""
                class Box<T> {
                    var value: T

                    init(value: T) {
                        this.value = value
                    }

                    func replaceWith<U>(value: U): U {
                        return value
                    }
                }

                func main(): Unit {
                    val box = Box(1)
                    println(box.replaceWith("hello"))
                    println(box.value)
                }
                """));
    }

    @Test
    public void genericInterfaceDispatchExecutes() {
        assertEquals("boxed\n", run("""
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

                func main(): Unit {
                    println(describe(StringBox()))
                }
                """));
    }

    @Test
    public void genericClassCanImplementAMatchingGenericInterface() {
        assertEquals("value\n", run("""
                interface Container<T> {
                    func get(): T
                }

                class Holder<T> implements Container<T> {
                    var value: T

                    init(value: T) {
                        this.value = value
                    }

                    func get(): T {
                        return this.value
                    }
                }

                func main(): Unit {
                    val holder = Holder("value")
                    val container: Container<String> = holder
                    println(container.get())
                }
                """));
    }

    @Test
    public void inheritedGenericMembersExecuteThroughASubclass() {
        assertEquals("9\n9\n", run("""
                open class Box<T> {
                    var value: T

                    init(value: T) {
                        this.value = value
                    }

                    open func get(): T {
                        return this.value
                    }
                }

                class IntBox extends Box<Int> {
                    init(value: Int) {
                        super(value)
                    }
                }

                func main(): Unit {
                    val box = IntBox(9)
                    println(box.value)
                    println(box.get())
                }
                """));
    }

    @Test
    public void genericClassDisplaysAsItsClassName() {
        assertEquals("Box\n", run("""
                class Box<T> {
                    var value: T

                    init(value: T) {
                        this.value = value
                    }
                }

                func main(): Unit {
                    println(Box(5))
                }
                """));
    }

    @Test
    public void genericInvarianceErrorSuppressesAllOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            PolyglotException failure = assertThrows(PolyglotException.class, () -> context.eval(build("""
                    class Box<T> {
                        var value: T

                        init(value: T) {
                            this.value = value
                        }
                    }

                    func main(): Unit {
                        println("before")
                        val box: Box<Int> = Box("x")
                        println("after")
                    }
                    """, "test.sol")));
            assertEquals(0, out.size());
            assertEquals(true, failure.isGuestException() || failure.isSyntaxError() || failure.isInternalError());
        }
    }
}
