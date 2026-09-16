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
 * End-to-end Phase 6 execution tests: class declarations, construction, instance methods,
 * {@code this}, and property reads/writes run through the Truffle AST backend. Assertions observe
 * {@code print}/{@code println} output so the tests exercise real construction and dispatch.
 */
public final class SolvikClassExecutionTest {

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
    public void constructsObjectAndReadsPropertiesAndMethods() {
        assertEquals("7\nDoug\nDoug\n", run("""
                class User {
                    val id: Int
                    var name: String

                    init(id: Int, name: String) {
                        this.id = id
                        this.name = name
                    }

                    fun describe(): String {
                        return this.name
                    }
                }

                fun main(): Unit {
                    val user = User(7, "Doug")
                    println(user.id)
                    println(user.name)
                    println(user.describe())
                }
                """));
    }

    @Test
    public void declarationInitializersRunWithoutAnInit() {
        assertEquals("2\nc\n", run("""
                class Counter {
                    var count: Int = 0
                    val label: String = "c"

                    fun increment(): Unit {
                        this.count = this.count + 1
                    }

                    fun value(): Int {
                        return this.count
                    }
                }

                fun main(): Unit {
                    val counter = Counter()
                    counter.increment()
                    counter.increment()
                    println(counter.value())
                    println(counter.label)
                }
                """));
    }

    @Test
    public void mutablePropertiesCanBeWrittenAfterConstruction() {
        assertEquals("42\n", run("""
                class Box {
                    var value: Int

                    init(start: Int) {
                        this.value = start
                    }
                }

                fun main(): Unit {
                    val box = Box(1)
                    box.value = box.value + 41
                    println(box.value)
                }
                """));
    }

    @Test
    public void unqualifiedMethodCallDispatchesOnThis() {
        assertEquals("Hello Doug\n", run("""
                class Greeter {
                    val name: String

                    init(name: String) {
                        this.name = name
                    }

                    fun greeting(): String {
                        return "Hello " + displayName()
                    }

                    fun displayName(): String {
                        return this.name
                    }
                }

                fun main(): Unit {
                    println(Greeter("Doug").greeting())
                }
                """));
    }

    @Test
    public void immutablePropertyAssignedInConstructorIsReadable() {
        assertEquals("5\n", run("""
                class Point {
                    val x: Int
                    val y: Int

                    init(x: Int, y: Int) {
                        this.x = x
                        this.y = y
                    }

                    fun sum(): Int {
                        return this.x + this.y
                    }
                }

                fun main(): Unit {
                    println(Point(2, 3).sum())
                }
                """));
    }

    @Test
    public void objectDisplaysAsItsClassName() {
        assertEquals("Empty\n", run("""
                class Empty {
                    val x: Int = 0
                }

                fun main(): Unit {
                    println(Empty())
                }
                """));
    }

    @Test
    public void objectsCompareByIdentity() {
        assertEquals("true\nfalse\n", run("""
                class Marker {
                    val id: Int
                    init(id: Int) {
                        this.id = id
                    }
                }

                fun main(): Unit {
                    val a = Marker(1)
                    val b = a
                    val c = Marker(1)
                    println(a == b)
                    println(a == c)
                }
                """));
    }

    @Test
    public void compileErrorInAClassPreventsAllOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            assertThrows(PolyglotException.class, () -> context.eval(build("""
                    fun main(): Unit {
                        println("before")
                    }

                    class Broken {
                        val value: Int
                    }
                    """, "broken.sol")));
        }
        assertEquals("", out.toString(StandardCharsets.UTF_8));
    }
}
