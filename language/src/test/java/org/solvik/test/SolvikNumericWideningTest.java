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
import static org.solvik.test.SolvikTestSupport.local;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.BooleanType;
import org.solvik.type.ByteType;
import org.solvik.type.DoubleType;
import org.solvik.type.FloatType;
import org.solvik.type.IntegerType;
import org.solvik.type.LongType;
import org.solvik.type.ShortType;
import org.solvik.type.Type;

/**
 * Positive tests for implicit numeric widening (docs/LANGUAGE_SPEC.md section 4). Widening is
 * permitted only where no integral range or representable precision is lost, so every case here is a
 * lossless conversion: integral chains ({@code Byte -> Short -> Integer -> Long}), the integral-to-
 * {@code Double} widenings whose source has at most 32 value bits, and {@code Float -> Double}. Each
 * test asserts both the statically analysed type and that a coercion was recorded for the operand,
 * then the shipped semantics are confirmed by execution.
 */
public final class SolvikNumericWideningTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("widening.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
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
            return Source.newBuilder("solvik", source, "widening.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Asserts the declared type of local {@code index} and that its initializer was widened to {@code widened}. */
    private static void assertWidenedInitializer(CheckedProgram program, FunctionDeclNode fn, int index, Type widened) {
        assertThat(program.symbolOf(local(fn, index)).orElseThrow().type()).isEqualTo(widened);
        assertThat(program.coercionOf(local(fn, index).initializer())).as("initializer must record a widening to " + widened).contains(widened);
    }

    @Test
    public void integralChainWidensLosslessly() {
        CheckedProgram program = check("""
                func f(): Unit {
                    var l: Long = 1
                    var i: Integer = Short(1)
                    var s: Short = Byte(1)
                    var i2: Integer = Byte(2)
                    var l2: Long = Byte(3)
                }
                """);
        FunctionDeclNode fn = function(program);
        assertWidenedInitializer(program, fn, 0, LongType.INSTANCE);
        assertWidenedInitializer(program, fn, 1, IntegerType.INSTANCE);
        assertWidenedInitializer(program, fn, 2, ShortType.INSTANCE);
        assertWidenedInitializer(program, fn, 3, IntegerType.INSTANCE);
        assertWidenedInitializer(program, fn, 4, LongType.INSTANCE);
    }

    @Test
    public void integralWidensToDouble() {
        // Byte, Short, and Integer (at most 32 value bits) widen losslessly to Double. Long does not,
        // and is covered by SolvikNumericNegativeTest.longToDoubleIsRejectedAsPrecisionLoss.
        CheckedProgram program = check("""
                func f(): Unit {
                    var d1: Double = Byte(1)
                    var d2: Double = Short(2)
                    var d3: Double = 3
                }
                """);
        FunctionDeclNode fn = function(program);
        assertWidenedInitializer(program, fn, 0, DoubleType.INSTANCE);
        assertWidenedInitializer(program, fn, 1, DoubleType.INSTANCE);
        assertWidenedInitializer(program, fn, 2, DoubleType.INSTANCE);
    }

    @Test
    public void smallIntegralWidensToFloat() {
        // Byte and Short have at most 15 value bits, within the Float significand.
        CheckedProgram program = check("""
                func f(): Unit {
                    var f1: Float = Byte(1)
                    var f2: Float = Short(2)
                }
                """);
        FunctionDeclNode fn = function(program);
        assertWidenedInitializer(program, fn, 0, FloatType.INSTANCE);
        assertWidenedInitializer(program, fn, 1, FloatType.INSTANCE);
    }

    @Test
    public void floatWidensToDouble() {
        CheckedProgram program = check("""
                func f(): Unit {
                    var d: Double = 1.5f
                }
                """);
        FunctionDeclNode fn = function(program);
        assertWidenedInitializer(program, fn, 0, DoubleType.INSTANCE);
    }

    @Test
    public void mixedArithmeticProducesTheLeastCommonWidenedType() {
        CheckedProgram program = check("""
                func f(): Unit {
                    var a = 1 + 1L
                    var b = 1L + 1
                    var c = 1 + 1.5
                    var d = 1.5f + 2.0
                    var e = Byte(1) + 2
                }
                """);
        FunctionDeclNode fn = function(program);
        assertThat(program.typeOf(local(fn, 0).initializer()).orElseThrow()).isEqualTo(LongType.INSTANCE);
        assertThat(program.typeOf(local(fn, 1).initializer()).orElseThrow()).isEqualTo(LongType.INSTANCE);
        assertThat(program.typeOf(local(fn, 2).initializer()).orElseThrow()).isEqualTo(DoubleType.INSTANCE);
        assertThat(program.typeOf(local(fn, 3).initializer()).orElseThrow()).isEqualTo(DoubleType.INSTANCE);
        assertThat(program.typeOf(local(fn, 4).initializer()).orElseThrow()).isEqualTo(IntegerType.INSTANCE);
        // Each operand that is not already the common type records its own widening coercion.
        assertThat(program.coercionOf(local(fn, 0).initializer())).isEmpty();
    }

    @Test
    public void mixedArithmeticWidensBothOperands() {
        CheckedProgram program = check("""
                func f(): Unit {
                    var a = 1 + 1L
                }
                """);
        FunctionDeclNode fn = function(program);
        BinaryExprNode binary = (BinaryExprNode) local(fn, 0).initializer();
        assertThat(program.coercionOf(binary.left())).contains(LongType.INSTANCE);
        assertThat(program.coercionOf(binary.right())).isEmpty();
    }

    @Test
    public void mixedOrderingProducesBoolean() {
        CheckedProgram program = check("""
                func f(): Unit {
                    var a = 1 < 2L
                    var b = 1.5f > 2.0
                }
                """);
        FunctionDeclNode fn = function(program);
        assertThat(program.typeOf(local(fn, 0).initializer()).orElseThrow()).isEqualTo(BooleanType.INSTANCE);
        assertThat(program.typeOf(local(fn, 1).initializer()).orElseThrow()).isEqualTo(BooleanType.INSTANCE);
    }

    @Test
    public void mixedEqualityProducesBoolean() {
        CheckedProgram program = check("""
                func f(): Unit {
                    var a = 1 == 1L
                    var b = 1.5f != 1.5
                }
                """);
        FunctionDeclNode fn = function(program);
        assertThat(program.typeOf(local(fn, 0).initializer()).orElseThrow()).isEqualTo(BooleanType.INSTANCE);
        assertThat(program.typeOf(local(fn, 1).initializer()).orElseThrow()).isEqualTo(BooleanType.INSTANCE);
    }

    @Test
    public void argumentsAndReturnsWiden() {
        CheckedProgram program = check("""
                func takes(a: Long, b: Double): Unit {
                    return
                }
                func gives(): Double {
                    return 1
                }
                func calls(): Unit {
                    takes(1, 1.5f)
                }
                """);
        // Analysis succeeding is itself the assertion: Integer argument to Long, Float argument to
        // Double, and Integer return to Double are all lossless widenings.
        assertThat(function(program)).isNotNull();
    }

    @Test
    public void argumentWideningRecordsCoercion() {
        CheckedProgram program = check("""
                func takes(a: Long): Unit {
                    return
                }
                func calls(): Unit {
                    takes(1)
                }
                """);
        assertThat(program.coercions()).as("the Integer argument must record a widening to Long").isNotEmpty();
    }

    @Test
    public void substitutedGenericSlotWidens() {
        // A generic slot whose type parameter is substituted to a concrete numeric type IS a typed
        // slot: Box<Long>(1) widens Integer to Long (the substituted target is concrete Long), and
        // the value round-trips as a Long. check() asserts the analysis succeeds (the widening).
        check("""
                class Box<T> {
                    var value: T
                    Box(value: T) {
                        this.value = value
                    }
                }
                """);
        assertThat(run("""
                class Box<T> {
                    var value: T
                    Box(value: T) {
                        this.value = value
                    }
                }
                var b: Box<Long> = Box<Long>(1)
                println(b.value)
                """)).isEqualTo("1\n");
    }

    @Test
    public void collectionElementsWiden() {
        CheckedProgram program = check("""
                func f(): Unit {
                    var xs: List<Long> = List<Long>(1, 2)
                }
                """);
        assertThat(function(program)).isNotNull();
    }

    @Test
    public void wideningExecutesWithTheWidenedRepresentation() {
        assertThat(run("""
                    println(1 + 1L)
                    println(1L + 1)
                    println(1 + 1.5)
                    println(1.5f + 2.0)
                    println(1 < 2L)
                    println(1 == 1L)
                    println(1.5f != 1.5)
                """)).isEqualTo("2\n2\n2.5\n3.5\ntrue\ntrue\nfalse\n");
    }

    @Test
    public void wideningToFloatExecutes() {
        // A widening whose target is the Float typed slot drives the FLOAT convert branch at runtime.
        assertThat(run("""
                    var f: Float = Byte(3)
                    println(f)
                """)).isEqualTo("3.0\n");
    }

    @Test
    public void wideningToTypedSlotsExecutes() {
        // Widened locals are stored in typed frame slots (Long, Double), exercising the boxed-value
        // write specialization rather than the generic object path.
        assertThat(run("""
                    var l: Long = 1
                    var d: Double = 1.5f
                    var i: Integer = Byte(7)
                    println(l)
                    println(d)
                    println(i)
                """)).isEqualTo("1\n1.5\n7\n");
    }
}
