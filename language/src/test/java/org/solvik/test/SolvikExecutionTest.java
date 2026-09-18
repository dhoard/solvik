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
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

/**
 * End-to-end Phase 5 execution tests: Solvik source is parsed, statically checked, lowered to the
 * Truffle AST backend, and executed through a polyglot context registered as {@code solvik}. Every
 * assertion observes the program's {@code print}/{@code println} output so the tests exercise real
 * lowering and execution rather than the AST alone.
 */
public final class SolvikExecutionTest {

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

    private static String runMain(String body) {
        return run(body);
    }

    @Test
    public void printsEveryScalarType() {
        assertThat(runMain("println(42)\nprintln(true)\nprintln(\"hello\")\nprintln(println(1))")).isEqualTo("42\ntrue\nhello\n1\nUnit\n");
    }

    @Test
    public void printDoesNotAppendNewline() {
        assertThat(runMain("print(\"a\")\nprint(\"b\")")).isEqualTo("ab");
    }

    @Test
    public void arithmeticPrecedenceAndCheckedOverflow() {
        assertThat(runMain("println(1 + 2 * 3)\nprintln(7 / 3)\nprintln(-6)\nprintln(8 - 3 * 2)")).isEqualTo("7\n2\n-6\n2\n");
    }

    @Test
    public void concatenationAndEquality() {
        assertThat(runMain("println(\"hello\" .. \" \" .. \"world\")\nprintln(\"a\" == \"a\")\nprintln(\"a\" == \"b\")")).isEqualTo("hello world\ntrue\nfalse\n");
    }

    @Test
    public void comparisonsAndBooleanOperators() {
        assertThat(runMain("""
                println(1 < 2)
                println(2 <= 1)
                println(1 + 1 == 2 && 3 > 2)
                println(false || false)
                println(!(1 == 2))
                """)).isEqualTo("true\nfalse\ntrue\nfalse\ntrue\n");
    }

    @Test
    public void shortCircuitAvoidsEvaluatingRight() {
        assertThat(runMain("""
                println(true || (1 / 0 == 0))
                println(false && (1 / 0 == 0))
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void localsInferenceAndMutation() {
        assertThat(runMain("""
                val a = 1
                var b = 2
                b = b + a
                println(b)
                """)).isEqualTo("3\n");
    }

    @Test
    public void ifElseChains() {
        assertThat(runMain("""
                val x = 5
                if (x < 3) {
                    println("tiny")
                } else if (x < 10) {
                    println("small")
                } else {
                    println("big")
                }
                val y = 50
                if (y < 3) {
                    println("tiny")
                } else if (y < 10) {
                    println("small")
                } else {
                    println("medium")
                }
                """)).isEqualTo("small\nmedium\n");
    }

    @Test
    public void whileLoopWithBreakAndContinue() {
        assertThat(runMain("""
                var i = 0
                while (i < 6) {
                    i = i + 1
                    if (i == 2) {
                        continue
                    }
                    if (i == 5) {
                        break
                    }
                    println(i)
                }
                """)).isEqualTo("1\n3\n4\n");
    }

    @Test
    public void forLoopRunsUpdateAfterContinue() {
        assertThat(runMain("""
                for (var i = 0; i < 3; i = i + 1) {
                    if (i == 1) {
                        continue
                    }
                    println(i)
                }
                """)).isEqualTo("0\n2\n");
    }

    @Test
    public void forLoopBreakStopsIteration() {
        assertThat(runMain("""
                for (var i = 0; i < 100; i = i + 1) {
                    if (i == 2) {
                        break
                    }
                    println(i)
                }
                """)).isEqualTo("0\n1\n");
    }

    @Test
    public void functionsCallsAndRecursion() {
        assertThat(run("""
                func factorial(n: Int): Int {
                    if (n <= 1) {
                        return 1
                    }
                    return n * factorial(n - 1)
                }
                func fib(n: Int): Int {
                    if (n < 2) {
                        return n
                    }
                    return fib(n - 1) + fib(n - 2)
                }
                    println(factorial(5))
                    println(fib(10))
                """)).isEqualTo("120\n55\n");
    }

    @Test
    public void callArgumentListAcceptsATrailingComma() {
        assertThat(run("""
                func add(a: Int, b: Int): Int {
                    return a + b
                }
                    println(add(1, 2,))
                """)).isEqualTo("3\n");
    }

    @Test
    public void forwardAndMutuallyRecursiveCalls() {
        assertThat(run("""
                func isEven(n: Int): Boolean {
                    if (n == 0) {
                        return true
                    }
                    return isOdd(n - 1)
                }
                func isOdd(n: Int): Boolean {
                    if (n == 0) {
                        return false
                    }
                    return isEven(n - 1)
                }
                    println(isEven(10))
                """)).isEqualTo("true\n");
    }

    @Test
    public void rawStringsPreserveBackslashes() {
        assertThat(runMain("println(r#\"C:\\temp\\n\"#)")).isEqualTo("C:\\temp\\n\n");
    }

    @Test
    public void normalStringEscapesDecode() {
        assertThat(runMain("println(\"a\\tb\\nc\")")).isEqualTo("a\tb\nc\n");
    }

    @Test
    public void programWithoutMainIsValidAndDoesNothing() {
        assertThat(run("""
                func add(a: Int, b: Int): Int {
                    return a + b
                }
                """)).isEqualTo("");
    }

    @Test
    public void returnWithoutValueInUnitFunction() {
        assertThat(runMain("""
                println("done")
                return
                """)).isEqualTo("done\n");
    }
}
