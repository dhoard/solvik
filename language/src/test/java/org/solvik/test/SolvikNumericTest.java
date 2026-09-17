/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.BooleanType;
import org.solvik.type.ByteType;
import org.solvik.type.CharType;
import org.solvik.type.DoubleType;
import org.solvik.type.FloatType;
import org.solvik.type.IntType;
import org.solvik.type.LongType;
import org.solvik.type.ShortType;

/**
 * Positive Phase 7 tests for the built-in numeric types and {@code Char}: literal typing, explicit
 * conversion typing, same-type arithmetic, and end-to-end execution of every declared built-in.
 */
public final class SolvikNumericTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("numeric.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertTrue("analysis must succeed: " + result.diagnostics().all(), result.isSuccess());
        return result.requireProgram();
    }

    private static FunctionDeclNode function(CheckedProgram program) {
        return (FunctionDeclNode) program.unit().declarations().get(0);
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
            return Source.newBuilder("solvik", source, "numeric.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void numericAndCharLiteralsHaveTheirDeclaredTypes() {
        CheckedProgram program = check("""
                func f(): Unit {
                    val intValue = 1
                    val longValue = 1L
                    val floatValue = 1.5f
                    val doubleValue = 1.5
                    val exponentValue = 1e3
                    val charValue = 'A'
                }
                """);
        FunctionDeclNode fn = function(program);
        assertEquals(IntType.INSTANCE, program.symbolOf(SolvikTestSupport.local(fn, 0)).orElseThrow().type());
        assertEquals(LongType.INSTANCE, program.symbolOf(SolvikTestSupport.local(fn, 1)).orElseThrow().type());
        assertEquals(FloatType.INSTANCE, program.symbolOf(SolvikTestSupport.local(fn, 2)).orElseThrow().type());
        assertEquals(DoubleType.INSTANCE, program.symbolOf(SolvikTestSupport.local(fn, 3)).orElseThrow().type());
        assertEquals(DoubleType.INSTANCE, program.symbolOf(SolvikTestSupport.local(fn, 4)).orElseThrow().type());
        assertEquals(CharType.INSTANCE, program.symbolOf(SolvikTestSupport.local(fn, 5)).orElseThrow().type());
    }

    @Test
    public void explicitConversionsProduceTheirTargetTypes() {
        CheckedProgram program = check("""
                func f(): Unit {
                    val b = Byte(1)
                    val s = Short(1)
                    val i = Int(1L)
                    val l = Long(1)
                    val fl = Float(1)
                    val d = Double(1)
                }
                """);
        FunctionDeclNode fn = function(program);
        assertEquals(ByteType.INSTANCE, program.symbolOf(SolvikTestSupport.local(fn, 0)).orElseThrow().type());
        assertEquals(ShortType.INSTANCE, program.symbolOf(SolvikTestSupport.local(fn, 1)).orElseThrow().type());
        assertEquals(IntType.INSTANCE, program.symbolOf(SolvikTestSupport.local(fn, 2)).orElseThrow().type());
        assertEquals(LongType.INSTANCE, program.symbolOf(SolvikTestSupport.local(fn, 3)).orElseThrow().type());
        assertEquals(FloatType.INSTANCE, program.symbolOf(SolvikTestSupport.local(fn, 4)).orElseThrow().type());
        assertEquals(DoubleType.INSTANCE, program.symbolOf(SolvikTestSupport.local(fn, 5)).orElseThrow().type());
    }

    @Test
    public void sameTypeArithmeticProducesThatNumericType() {
        CheckedProgram program = check("""
                func f(): Unit {
                    val b = Byte(1) + Byte(2)
                    val s = Short(1) * Short(2)
                    val i = 1 + 2
                    val l = 1L - 2L
                    val fl = 1.5f / 2.5f
                    val d = 1.5 + 2.5
                }
                """);
        FunctionDeclNode fn = function(program);
        assertEquals(ByteType.INSTANCE, program.typeOf(SolvikTestSupport.local(fn, 0).initializer()).orElseThrow());
        assertEquals(ShortType.INSTANCE, program.typeOf(SolvikTestSupport.local(fn, 1).initializer()).orElseThrow());
        assertEquals(IntType.INSTANCE, program.typeOf(SolvikTestSupport.local(fn, 2).initializer()).orElseThrow());
        assertEquals(LongType.INSTANCE, program.typeOf(SolvikTestSupport.local(fn, 3).initializer()).orElseThrow());
        assertEquals(FloatType.INSTANCE, program.typeOf(SolvikTestSupport.local(fn, 4).initializer()).orElseThrow());
        assertEquals(DoubleType.INSTANCE, program.typeOf(SolvikTestSupport.local(fn, 5).initializer()).orElseThrow());
    }

    @Test
    public void sameTypeOrderingProducesBoolean() {
        CheckedProgram program = check("""
                func f(): Unit {
                    val b = Byte(1) < Byte(2)
                    val s = Short(1) >= Short(2)
                    val i = 1 > 2
                    val l = 1L <= 2L
                    val fl = 1.5f < 2.5f
                    val d = 1.5 > 2.5
                }
                """);
        FunctionDeclNode fn = function(program);
        for (int i = 0; i <= 5; i++) {
            assertEquals(BooleanType.INSTANCE, program.typeOf(SolvikTestSupport.local(fn, i).initializer()).orElseThrow());
        }
    }

    @Test
    public void negationKeepsTheNumericType() {
        CheckedProgram program = check("""
                func f(): Unit {
                    val l = -1L
                    val d = -1.5
                    val fl = -1.5f
                }
                """);
        FunctionDeclNode fn = function(program);
        assertEquals(LongType.INSTANCE, program.typeOf(SolvikTestSupport.local(fn, 0).initializer()).orElseThrow());
        assertEquals(DoubleType.INSTANCE, program.typeOf(SolvikTestSupport.local(fn, 1).initializer()).orElseThrow());
        assertEquals(FloatType.INSTANCE, program.typeOf(SolvikTestSupport.local(fn, 2).initializer()).orElseThrow());
    }

    @Test
    public void numberAcceptsEveryNumericSubtype() {
        check("""
                func f(): Unit {
                    val a: Number = 1
                    val b: Number = 1L
                    val c: Number = 1.5f
                    val d: Number = 1.5
                    val e: Object = 1
                }
                """);
    }

    @Test
    public void everyBuiltinDisplaysItsValue() {
        assertEquals("1\n2\n3\n4\n1.5\n2.5\nA\n", run("""
                func main(): Unit {
                    println(Byte(1))
                    println(Short(2))
                    println(3)
                    println(4L)
                    println(1.5f)
                    println(2.5)
                    println('A')
                }
                """));
    }

    @Test
    public void arithmeticExecutesForEveryNumericType() {
        assertEquals("3\n30\n5\n4.0\n1.5\n", run("""
                func main(): Unit {
                    println(Byte(1) + Byte(2))
                    println(Short(10) * Short(3))
                    println(7L - 2L)
                    println(1.5f + 2.5f)
                    println(1.0 + 0.5)
                }
                """));
    }

    @Test
    public void explicitConversionsExecuteWithTruncation() {
        assertEquals("5\n100\n3.0\n2\n", run("""
                func main(): Unit {
                    println(Long(5))
                    println(Byte(100))
                    println(Double(3))
                    println(Int(2.9))
                }
                """));
    }

    @Test
    public void characterEqualityComparesByValue() {
        assertEquals("true\nfalse\n", run("""
                func main(): Unit {
                    println('A' == 'A')
                    println('A' == 'B')
                }
                """));
    }

    @Test
    public void integralOverflowAtRuntimeRaisesAnArithmeticError() {
        PolyglotException failure = evaluate("func main(): Unit {\n    println(Byte(100) + Byte(100))\n}\n");
        assertNotNull(failure);
        assertFalse(failure.isSyntaxError());
        assertTrue(failure.getMessage(), failure.getMessage().contains("overflow"));
    }

    @Test
    public void integralConversionOutOfRangeAtRuntimeRaisesAnArithmeticError() {
        PolyglotException failure = evaluate("func main(): Unit {\n    val x = Int(1000)\n    println(Byte(x))\n}\n");
        assertNotNull(failure);
        assertFalse(failure.isSyntaxError());
        assertTrue(failure.getMessage(), failure.getMessage().contains("out of range"));
    }

    private static PolyglotException evaluate(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source));
        } catch (PolyglotException e) {
            return e;
        }
        return null;
    }
}
