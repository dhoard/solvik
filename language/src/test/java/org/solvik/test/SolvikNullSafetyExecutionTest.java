/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Test;

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
        assertEquals("true\n7\n", run(BOX + """
                    val a: Box? = null
                    val b: Box? = Box(7)
                    println(a == null)
                    println(b?.value ?? -1)
                """));
    }

    @Test
    public void coalescingUsesTheFallbackForNull() {
        assertEquals("fallback\nvalue\n", run("""
                    val s: String? = null
                    val t: String? = "value"
                    println(s ?? "fallback")
                    println(t ?? "fallback")
                """));
    }

    @Test
    public void coalescingDoesNotEvaluateTheFallbackWhenTheLeftIsNotNull() {
        assertEquals("value\n", run("""
                func side(): String {
                    println("side")
                    return "fallback"
                }
                    val s: String? = "value"
                    println(s ?? side())
                """));
    }

    @Test
    public void safeMethodCallDoesNotEvaluateArgumentsForANullReceiver() {
        assertEquals("-1\n", run("""
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
                """));
    }

    @Test
    public void typeTestUsesTheRuntimeType() {
        assertEquals("true\nfalse\n", run("""
                class Box {
                }
                    val v: Any = Box()
                    println(v is Box)
                    println(v is String)
                """));
    }

    @Test
    public void typeTestWorksForInterfaces() {
        assertEquals("true\nDoug\n", run("""
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
                """));
    }

    @Test
    public void checkedCastReturnsTheValueOnSuccess() {
        assertEquals("3\n", run(BOX + """
                    val v: Any = Box(3)
                    val box = v as Box
                    println(box.value)
                """));
    }

    @Test
    public void unsuccessfulCastRaisesARuntimeTypeError() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotException failure = runFailing(BOX + """
                    val v: Any = "not a box"
                    val box = v as Box
                    println(box.value)
                """, out);
        assertNotNull("the cast must fail", failure);
        assertTrue("a guest exception is reported", failure.isGuestException());
        assertTrue("the message names the target type: " + failure.getMessage(), failure.getMessage().contains("Box"));
        assertEquals("nothing is printed before the cast", "", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void nullCheckNarrowingExecutesTheNonNullBranch() {
        assertEquals("5\n", run(BOX + """
                    val box: Box? = Box(5)
                    if (box != null) {
                        println(box.value)
                    }
                """));
    }

    @Test
    public void typeTestNarrowingExecutesTheNonNullBranch() {
        assertEquals("9\n", run(BOX + """
                    val v: Any = Box(9)
                    if (v is Box) {
                        println(v.value)
                    }
                """));
    }

    @Test
    public void builtinTypeTestsAndCastsExecute() {
        assertEquals("true\nfalse\n2\n", run("""
                    val v: Any = 1
                    println(v is Int)
                    println(v is String)
                    val n = v as Int
                    println(n + 1)
                """));
    }

    @Test
    public void inheritanceTypeTestsWalkTheRuntimeClassChain() {
        assertEquals("true\ntrue\nwoof\n", run("""
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
                """));
    }
}
