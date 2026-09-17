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
 * End-to-end Phase 12 execution tests (docs/LANGUAGE_SPEC.md section 12): enum variant construction,
 * value equality, display, type tests, and sealed-class dispatch run through the Truffle AST backend.
 */
public final class SolvikEnumExecutionTest {

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
    public void valueLessEnumValuesCompareByValue() {
        assertEquals("true\nfalse\n", run("""
                enum Color {
                    Red
                    Blue
                }

                func main(): Unit {
                    println(Color.Red == Color.Red)
                    println(Color.Red == Color.Blue)
                }
                """));
    }

    @Test
    public void valueCarryingVariantsCompareTheirValues() {
        assertEquals("true\nfalse\nfalse\n", run("""
                enum Result {
                    Ok(Int)
                    Error(String)
                }

                func main(): Unit {
                    println(Result.Ok(5) == Result.Ok(5))
                    println(Result.Ok(5) == Result.Ok(6))
                    println(Result.Ok(5) == Result.Error("x"))
                }
                """));
    }

    @Test
    public void genericVariantConstructionExecutes() {
        assertEquals("true\n", run("""
                enum Option<T> {
                    Some(T)
                }

                func main(): Unit {
                    val some: Option<Int> = Option.Some(7)
                    println(some == Option.Some(7))
                }
                """));
    }

    @Test
    public void enumValuesFlowThroughFunctionsAndVariables() {
        assertEquals("true\n", run("""
                enum Color {
                    Red
                    Blue
                }

                func pick(): Color {
                    return Color.Blue
                }

                func main(): Unit {
                    val color: Color = pick()
                    println(color == Color.Blue)
                }
                """));
    }

    @Test
    public void enumValuesDisplayAsTheirEnumTypeName() {
        assertEquals("Color\n", run("""
                enum Color {
                    Red
                }

                func main(): Unit {
                    println(Color.Red)
                }
                """));
    }

    @Test
    public void enumTypeTestsExecute() {
        assertEquals("true\nfalse\n", run("""
                enum Color {
                    Red
                }

                func isColor(value: Any): Boolean {
                    return value is Color
                }

                func main(): Unit {
                    println(isColor(Color.Red))
                    println(isColor("red"))
                }
                """));
    }

    @Test
    public void sealedHierarchyDispatchesThroughTheSealedType() {
        assertEquals("circle\nsquare\n", run("""
                sealed class Shape {
                    open func name(): String {
                        return "shape"
                    }
                }

                class Circle extends Shape {
                    override func name(): String {
                        return "circle"
                    }
                }

                class Square extends Shape {
                    override func name(): String {
                        return "square"
                    }
                }

                func describe(shape: Shape): String {
                    return shape.name()
                }

                func main(): Unit {
                    val circle: Shape = Circle()
                    val square: Shape = Square()
                    println(describe(circle))
                    println(describe(square))
                }
                """));
    }

    @Test
    public void enumVariantConstructionErrorSuppressesAllOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            PolyglotException failure = assertThrows(PolyglotException.class, () -> context.eval(build("""
                    enum Result {
                        Ok(Int)
                    }

                    func main(): Unit {
                        println("before")
                        val bad: Result = Result.Ok("x")
                        println("after")
                    }
                    """, "test.sol")));
            assertEquals(0, out.size());
            assertEquals(true, failure.isGuestException() || failure.isSyntaxError() || failure.isInternalError());
        }
    }
}
