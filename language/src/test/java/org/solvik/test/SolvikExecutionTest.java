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

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Test;

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
        assertEquals("42\ntrue\nhello\n1\nUnit\n", runMain("println(42)\nprintln(true)\nprintln(\"hello\")\nprintln(println(1))"));
    }

    @Test
    public void printDoesNotAppendNewline() {
        assertEquals("ab", runMain("print(\"a\")\nprint(\"b\")"));
    }

    @Test
    public void arithmeticPrecedenceAndCheckedOverflow() {
        assertEquals("7\n2\n-6\n2\n", runMain("println(1 + 2 * 3)\nprintln(7 / 3)\nprintln(-6)\nprintln(8 - 3 * 2)"));
    }

    @Test
    public void concatenationAndEquality() {
        assertEquals("hello world\ntrue\nfalse\n", runMain("println(\"hello\" .. \" \" .. \"world\")\nprintln(\"a\" == \"a\")\nprintln(\"a\" == \"b\")"));
    }

    @Test
    public void comparisonsAndBooleanOperators() {
        assertEquals("true\nfalse\ntrue\nfalse\ntrue\n", runMain("""
                println(1 < 2)
                println(2 <= 1)
                println(1 + 1 == 2 && 3 > 2)
                println(false || false)
                println(!(1 == 2))
                """));
    }

    @Test
    public void shortCircuitAvoidsEvaluatingRight() {
        assertEquals("true\nfalse\n", runMain("""
                println(true || (1 / 0 == 0))
                println(false && (1 / 0 == 0))
                """));
    }

    @Test
    public void localsInferenceAndMutation() {
        assertEquals("3\n", runMain("""
                val a = 1
                var b = 2
                b = b + a
                println(b)
                """));
    }

    @Test
    public void ifElseChains() {
        assertEquals("small\nmedium\n", runMain("""
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
                """));
    }

    @Test
    public void whileLoopWithBreakAndContinue() {
        assertEquals("1\n3\n4\n", runMain("""
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
                """));
    }

    @Test
    public void forLoopRunsUpdateAfterContinue() {
        assertEquals("0\n2\n", runMain("""
                for (var i = 0; i < 3; i = i + 1) {
                    if (i == 1) {
                        continue
                    }
                    println(i)
                }
                """));
    }

    @Test
    public void forLoopBreakStopsIteration() {
        assertEquals("0\n1\n", runMain("""
                for (var i = 0; i < 100; i = i + 1) {
                    if (i == 2) {
                        break
                    }
                    println(i)
                }
                """));
    }

    @Test
    public void functionsCallsAndRecursion() {
        assertEquals("120\n55\n", run("""
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
                """));
    }

    @Test
    public void forwardAndMutuallyRecursiveCalls() {
        assertEquals("true\n", run("""
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
                """));
    }

    @Test
    public void rawStringsPreserveBackslashes() {
        assertEquals("C:\\temp\\n\n", runMain("println(r#\"C:\\temp\\n\"#)"));
    }

    @Test
    public void normalStringEscapesDecode() {
        assertEquals("a\tb\nc\n", runMain("println(\"a\\tb\\nc\")"));
    }

    @Test
    public void programWithoutMainIsValidAndDoesNothing() {
        assertEquals("", run("""
                func add(a: Int, b: Int): Int {
                    return a + b
                }
                """));
    }

    @Test
    public void returnWithoutValueInUnitFunction() {
        assertEquals("done\n", runMain("""
                println("done")
                return
                """));
    }
}
