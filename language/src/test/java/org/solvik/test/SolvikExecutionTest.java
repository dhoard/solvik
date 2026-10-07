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
        try (Context context = Context.newBuilder("solvik").out(out).err(out)
                .option("engine.WarnInterpreterOnly", "false").allowAllAccess(true).build()) {
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
        // A call that produces no value is not a value, so `println(...)` cannot be nested as an
        // argument; the scalar types are what this test covers.
        assertThat(runMain("println(42)\nprintln(true)\nprintln(\"hello\")\nprintln('a')\nprintln(1.5)"))
                .isEqualTo("42\ntrue\nhello\na\n1.5\n");
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
                var a: Integer = 1
                var mutable b: Integer = 2
                b = b + a
                println(b)

                """)).isEqualTo("3\n");
    }

    @Test
    public void ifElseChains() {
        assertThat(runMain("""
                var x: Integer = 5
                if (x < 3) {
                    println("tiny")
                }
                else if (x < 10) {
                    println("small")
                }
                else {
                    println("big")
                }
                var y: Integer = 50
                if (y < 3) {
                    println("tiny")
                }
                else if (y < 10) {
                    println("small")
                }
                else {
                    println("medium")
                }

                """)).isEqualTo("small\nmedium\n");
    }

    @Test
    public void whileLoopWithBreakAndContinue() {
        assertThat(runMain("""
                var mutable i: Integer = 0
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
    public void whileSkipsAnElementWithoutAForUpdateClause() {
        // The removed three-clause `for` guaranteed an update after `continue`; the
        // `while` replacement has no update clause, so a skip is written as a guard
        // and the induction step always runs as the body's final item.
        assertThat(runMain("""
                {
                    var mutable i: Integer = 0
                    while (i < 3) {
                        if (i != 1) {
                            println(i)
                        }
                        i = i + 1
                    }
                }

                """)).isEqualTo("0\n2\n");
    }

    @Test
    public void whileLoopBreakStopsIteration() {
        assertThat(runMain("""
                {
                    var mutable i: Integer = 0
                    while (i < 100) {
                        if (i == 2) {
                            break
                        }
                        println(i)
                        i = i + 1
                    }
                }

                """)).isEqualTo("0\n1\n");
    }

    @Test
    public void functionsCallsAndRecursion() {
        assertThat(run("""
                func factorial(n: Integer): Integer {
                    if (n <= 1) {
                        return 1
                    }
                    return n * factorial(n - 1)
                }
                func fib(n: Integer): Integer {
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
                func add(a: Integer, b: Integer): Integer {
                    return a + b
                }
                    println(add(1, 2,))
                """)).isEqualTo("3\n");
    }

    @Test
    public void forwardAndMutuallyRecursiveCalls() {
        assertThat(run("""
                func isEven(n: Integer): Boolean {
                    if (n == 0) {
                        return true
                    }
                    return isOdd(n - 1)
                }
                func isOdd(n: Integer): Boolean {
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

    /**
     * Verifies the platform-native {@code \N} escape expands at runtime through
     * {@code System#lineSeparator()}, and that it is distinct from the always-LF {@code \n}.
     */
    @Test
    public void nativeLineSeparatorEscapeExpandsAtRuntime() {
        // \N expands to the native line separator at runtime; println appends its own newline.
        assertThat(runMain("println(\"\\N\")")).isEqualTo(System.lineSeparator() + System.lineSeparator());
        // \n is always LF, regardless of platform.
        assertThat(runMain("println(\"\\n\")")).isEqualTo("\n\n");
        // \r\n is always CRLF.
        assertThat(runMain("println(\"\\r\\n\")")).isEqualTo("\r\n\n");
        // Multiple consecutive \N each expand independently.
        assertThat(runMain("println(\"\\N\\N\")")).isEqualTo(System.lineSeparator() + System.lineSeparator() + System.lineSeparator());
        // Mixed with other escapes.
        assertThat(runMain("println(\"\\n\\N\\r\")")).isEqualTo("\n" + System.lineSeparator() + "\r\n");
        // Escaped backslash followed by N yields literal backslash + N.
        assertThat(runMain("println(\"\\\\N\")")).isEqualTo("\\N\n");
    }

    /** Verifies that raw strings preserve literal {@code \N}. */
    @Test
    public void rawStringPreservesLiteralBackslashN() {
        assertThat(runMain("println(r#\"raw \\N\"#)")).isEqualTo("raw \\N\n");
    }

    @Test
    public void programWithoutMainIsValidAndDoesNothing() {
        assertThat(run("""
                func add(a: Integer, b: Integer): Integer {
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

    @Test
    public void whileWithCompoundAndOrConditionsExecutesAtRuntime() {
        // Exercises the lowering of compound && / || boolean conditions in a while-loop
        // at runtime (both short-circuit branches), confirming the lowering pipeline
        // for complex while conditions rather than only scalar conditions.
        assertThat(runMain("""
                var mutable i: Integer = 0
                var mutable j: Integer = 0
                while (i < 3 && j < 3) {
                    i = i + 1
                    j = j + 1
                }
                println(i)
                println(j)

                """)).isEqualTo("3\n3\n");

        assertThat(runMain("""
                var mutable i: Integer = 0
                var mutable j: Integer = 0
                while (i < 3 || j < 2) {
                    i = i + 1
                    if (i > 2) {
                        break
                    }
                    j = j + 1
                }
                println(i)
                println(j)

                """)).isEqualTo("3\n2\n");
    }

    @Test
    public void whileTrueLoopsTerminateThroughBreakAtRuntime() {
        // The scope-plus-while idiom covers both shapes the removed three-clause
        // `for` used to reach: an infinite loop and a bare update-only loop.
        assertThat(runMain("""
                var mutable i: Integer = 0
                while (true) {
                    i = i + 1
                    if (i >= 4) {
                        break
                    }
                }
                println(i)

                """)).isEqualTo("4\n");

        assertThat(runMain("""
                var mutable i: Integer = 0
                {
                    while (true) {
                        if (i >= 3) {
                            break
                        }
                        i = i + 1
                    }
                }
                println(i)

                """)).isEqualTo("3\n");
    }
}
