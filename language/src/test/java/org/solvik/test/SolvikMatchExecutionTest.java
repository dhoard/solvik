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
 * End-to-end Phase 13 execution tests (docs/LANGUAGE_SPEC.md section 12): a lowered {@code match}
 * selects the first matching branch, destructures enum and sealed values, and honors the wildcard,
 * all through the Truffle AST backend.
 */
public final class SolvikMatchExecutionTest {

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
    public void valueLessVariantsSelectTheMatchingBranch() {
        assertEquals("red\nblue\nred\n", run("""
                enum Color {
                    Red
                    Blue
                }

                func name(color: Color): String {
                    return match color {
                        Red => "red"
                        Blue => "blue"
                    }
                }

                func main(): Unit {
                    println(name(Color.Red))
                    println(name(Color.Blue))
                    println(name(Color.Red))
                }
                """));
    }

    @Test
    public void valueCarryingVariantsDestructureTheirValues() {
        assertEquals("5\n-3\n", run("""
                enum Result {
                    Ok(Int)
                    Error(Int)
                }

                func value(result: Result): Int {
                    return match result {
                        Ok(value) => value
                        Error(code) => 0 - code
                    }
                }

                func main(): Unit {
                    println(value(Result.Ok(5)))
                    println(value(Result.Error(3)))
                }
                """));
    }

    @Test
    public void wildcardHandlesUnlistedVariants() {
        assertEquals("1\n0\n0\n", run("""
                enum Color {
                    Red
                    Blue
                    Green
                }

                func label(color: Color): Int {
                    return match color {
                        Red => 1
                        _ => 0
                    }
                }

                func main(): Unit {
                    println(label(Color.Red))
                    println(label(Color.Blue))
                    println(label(Color.Green))
                }
                """));
    }

    @Test
    public void sealedSubtypeBindingsAccessSubtypeMembers() {
        assertEquals("9\n16\n", run("""
                sealed class Shape {
                }

                class Circle extends Shape {
                    val radius: Int

                    init(radius: Int) {
                        this.radius = radius
                    }
                }

                class Square extends Shape {
                    val side: Int

                    init(side: Int) {
                        this.side = side
                    }
                }

                func area(shape: Shape): Int {
                    return match shape {
                        circle: Circle => circle.radius * circle.radius
                        square: Square => square.side * square.side
                    }
                }

                func main(): Unit {
                    println(area(Circle(3)))
                    println(area(Square(4)))
                }
                """));
    }

    @Test
    public void nestedVariantPatternsDestructure() {
        assertEquals("7\n-1\n-1\n", run("""
                enum Inner {
                    Some(Int)
                    None
                }

                enum Outer {
                    Wrap(Inner)
                    Empty
                }

                func unwrap(outer: Outer): Int {
                    return match outer {
                        Wrap(Some(value)) => value
                        Wrap(None) => 0 - 1
                        Empty => 0 - 1
                    }
                }

                func main(): Unit {
                    println(unwrap(Outer.Wrap(Inner.Some(7))))
                    println(unwrap(Outer.Wrap(Inner.None)))
                    println(unwrap(Outer.Empty))
                }
                """));
    }

    @Test
    public void genericEnumBindingsCarryTheSubstitutedValue() {
        assertEquals("4\n", run("""
                enum Box<T> {
                    Value(T)
                }

                func value(box: Box<Int>): Int {
                    return match box {
                        Value(x) => x
                    }
                }

                func main(): Unit {
                    println(value(Box.Value(4)))
                }
                """));
    }

    @Test
    public void matchIsUsableAsALocalInitializer() {
        assertEquals("other\n", run("""
                enum Color {
                    Red
                    Blue
                }

                func main(): Unit {
                    val color: Color = Color.Blue
                    val label = match color {
                        Red => "primary"
                        Blue => "other"
                    }
                    println(label)
                }
                """));
    }

    @Test
    public void matchResultFlowsThroughASealedSupertype() {
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

                func pick(shape: Shape): Shape {
                    return match shape {
                        circle: Circle => circle
                        square: Square => square
                    }
                }

                func main(): Unit {
                    println(pick(Circle()).name())
                    println(pick(Square()).name())
                }
                """));
    }

    @Test
    public void aNonExhaustiveMatchSuppressesAllOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            PolyglotException failure = assertThrows(PolyglotException.class, () -> context.eval(build("""
                    enum Color {
                        Red
                        Blue
                    }

                    func name(color: Color): String {
                        return match color {
                            Red => "red"
                        }
                    }

                    func main(): Unit {
                        println("before")
                        println(name(Color.Blue))
                    }
                    """, "test.sol")));
            assertEquals(0, out.size());
            assertEquals(true, failure.isGuestException() || failure.isSyntaxError() || failure.isInternalError());
        }
    }
}
