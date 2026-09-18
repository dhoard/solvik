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
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
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
        assertThat(program.symbolOf(SolvikTestSupport.local(fn, 0)).orElseThrow().type()).isEqualTo(IntType.INSTANCE);
        assertThat(program.symbolOf(SolvikTestSupport.local(fn, 1)).orElseThrow().type()).isEqualTo(LongType.INSTANCE);
        assertThat(program.symbolOf(SolvikTestSupport.local(fn, 2)).orElseThrow().type()).isEqualTo(FloatType.INSTANCE);
        assertThat(program.symbolOf(SolvikTestSupport.local(fn, 3)).orElseThrow().type()).isEqualTo(DoubleType.INSTANCE);
        assertThat(program.symbolOf(SolvikTestSupport.local(fn, 4)).orElseThrow().type()).isEqualTo(DoubleType.INSTANCE);
        assertThat(program.symbolOf(SolvikTestSupport.local(fn, 5)).orElseThrow().type()).isEqualTo(CharType.INSTANCE);
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
        assertThat(program.symbolOf(SolvikTestSupport.local(fn, 0)).orElseThrow().type()).isEqualTo(ByteType.INSTANCE);
        assertThat(program.symbolOf(SolvikTestSupport.local(fn, 1)).orElseThrow().type()).isEqualTo(ShortType.INSTANCE);
        assertThat(program.symbolOf(SolvikTestSupport.local(fn, 2)).orElseThrow().type()).isEqualTo(IntType.INSTANCE);
        assertThat(program.symbolOf(SolvikTestSupport.local(fn, 3)).orElseThrow().type()).isEqualTo(LongType.INSTANCE);
        assertThat(program.symbolOf(SolvikTestSupport.local(fn, 4)).orElseThrow().type()).isEqualTo(FloatType.INSTANCE);
        assertThat(program.symbolOf(SolvikTestSupport.local(fn, 5)).orElseThrow().type()).isEqualTo(DoubleType.INSTANCE);
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
        assertThat(program.typeOf(SolvikTestSupport.local(fn, 0).initializer()).orElseThrow()).isEqualTo(ByteType.INSTANCE);
        assertThat(program.typeOf(SolvikTestSupport.local(fn, 1).initializer()).orElseThrow()).isEqualTo(ShortType.INSTANCE);
        assertThat(program.typeOf(SolvikTestSupport.local(fn, 2).initializer()).orElseThrow()).isEqualTo(IntType.INSTANCE);
        assertThat(program.typeOf(SolvikTestSupport.local(fn, 3).initializer()).orElseThrow()).isEqualTo(LongType.INSTANCE);
        assertThat(program.typeOf(SolvikTestSupport.local(fn, 4).initializer()).orElseThrow()).isEqualTo(FloatType.INSTANCE);
        assertThat(program.typeOf(SolvikTestSupport.local(fn, 5).initializer()).orElseThrow()).isEqualTo(DoubleType.INSTANCE);
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
            assertThat(program.typeOf(SolvikTestSupport.local(fn, i).initializer()).orElseThrow()).isEqualTo(BooleanType.INSTANCE);
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
        assertThat(program.typeOf(SolvikTestSupport.local(fn, 0).initializer()).orElseThrow()).isEqualTo(LongType.INSTANCE);
        assertThat(program.typeOf(SolvikTestSupport.local(fn, 1).initializer()).orElseThrow()).isEqualTo(DoubleType.INSTANCE);
        assertThat(program.typeOf(SolvikTestSupport.local(fn, 2).initializer()).orElseThrow()).isEqualTo(FloatType.INSTANCE);
    }

    @Test
    public void numberAcceptsEveryNumericSubtype() {
        check("""
                func f(): Unit {
                    val a: Number = 1
                    val b: Number = 1L
                    val c: Number = 1.5f
                    val d: Number = 1.5
                    val e: Any = 1
                }
                """);
    }

    @Test
    public void everyBuiltinDisplaysItsValue() {
        assertThat(run("""
                    println(Byte(1))
                    println(Short(2))
                    println(3)
                    println(4L)
                    println(1.5f)
                    println(2.5)
                    println('A')
                """)).isEqualTo("1\n2\n3\n4\n1.5\n2.5\nA\n");
    }

    @Test
    public void arithmeticExecutesForEveryNumericType() {
        assertThat(run("""
                    println(Byte(1) + Byte(2))
                    println(Short(10) * Short(3))
                    println(7L - 2L)
                    println(1.5f + 2.5f)
                    println(1.0 + 0.5)
                """)).isEqualTo("3\n30\n5\n4.0\n1.5\n");
    }

    @Test
    public void explicitConversionsExecuteWithTruncation() {
        assertThat(run("""
                    println(Long(5))
                    println(Byte(100))
                    println(Double(3))
                    println(Int(2.9))
                """)).isEqualTo("5\n100\n3.0\n2\n");
    }

    @Test
    public void characterEqualityComparesByValue() {
        assertThat(run("""
                    println('A' == 'A')
                    println('A' == 'B')
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void integralOverflowAtRuntimeRaisesAnArithmeticError() {
        PolyglotException failure = evaluate("    println(Byte(100) + Byte(100))\n");
        assertThat(failure).isNotNull();
        assertThat(failure.isSyntaxError()).isFalse();
        assertThat(failure.getMessage().contains("overflow")).as(failure.getMessage()).isTrue();
    }

    @Test
    public void integralConversionOutOfRangeAtRuntimeRaisesAnArithmeticError() {
        PolyglotException failure = evaluate("    val x = Int(1000)\n    println(Byte(x))\n");
        assertThat(failure).isNotNull();
        assertThat(failure.isSyntaxError()).isFalse();
        assertThat(failure.getMessage().contains("out of range")).as(failure.getMessage()).isTrue();
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
