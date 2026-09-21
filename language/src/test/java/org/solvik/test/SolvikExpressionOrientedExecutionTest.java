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

/** Scratch execution checks for expression-oriented constructs. */
public final class SolvikExpressionOrientedExecutionTest {

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
    public void blockExpressionComputesValue() {
        assertThat(run("""
                val answer = {
                    val base = 20
                    base + 22
                }
                println(answer)

                val same = { 42 }
                println(same)

                val semi = {
                    42;
                }
                println(semi)
                """)).isEqualTo("42\n42\n42\n");
    }

    @Test
    public void unitBlockExpressionIsDistinguishedFromMissingTail() {
        assertThat(run("""
                val logged: Unit = {
                    println("done")
                }
                println(logged)
                """)).isEqualTo("done\nUnit\n");
    }

    @Test
    public void ifExpressionJoinsBranches() {
        assertThat(run("""
                func describe(value: Integer): String {
                    return if (value < 0) {
                        "negative"
                    } else if (value == 0) {
                        "zero"
                    } else {
                        "positive"
                    }
                }

                println(describe(-1))
                println(describe(0))
                println(describe(1))

                var score: Integer = 0
                score = if (true) { 10 } else { 0 }
                println(score)
                """)).isEqualTo("negative\nzero\npositive\n10\n");
    }

    @Test
    public void switchExpressionDispatchesOnce() {
        assertThat(run("""
                func message(status: Integer): String {
                    return switch (status) {
                        case 1:
                            "ready"
                        case 2, 3:
                            "busy"
                        default:
                            "done"
                    }
                }

                println(message(1))
                println(message(2))
                println(message(9))
                """)).isEqualTo("ready\nbusy\ndone\n");
    }

    @Test
    public void matchBranchBlockExpression() {
        assertThat(run("""
                enum Result {
                    Ok(Integer)
                    Error(String)
                }

                func describe(result: Result): String {
                    return match result {
                        Ok(value) => {
                            println("ok")
                            "value=" .. value
                        }
                        Error(message) => message
                    }
                }

                println(describe(Result.Ok(7)))
                """)).isEqualTo("ok\nvalue=7\n");
    }

    @Test
    public void abruptBranchProducesTheOtherBranchValue() {
        assertThat(run("""
                func requireName(name: String?): String {
                    return if (name != null) {
                        name
                    } else {
                        return "fallback"
                    }
                }

                println(requireName(null))
                println(requireName("Doug"))
                """)).isEqualTo("fallback\nDoug\n");
    }

    @Test
    public void switchExpressionEvaluatesItsScrutineeOnce() {
        assertThat(run("""
                class Counter {
                    var value: Integer

                    Counter() {
                        this.value = 0
                    }

                    func next(): Integer {
                        this.value = this.value + 1
                        return this.value
                    }
                }

                func label(counter: Counter): String {
                    return switch (counter.next()) {
                        case 1:
                            "first"
                        default:
                            "later"
                    }
                }

                val counter = Counter()
                println(label(counter))
                println(counter.value)
                """)).isEqualTo("first\n1\n");
    }

    @Test
    public void regexCaseDispatchInExpressionSwitch() {
        assertThat(run("""
                func kind(input: String): String {
                    return switch (input) {
                        case regex r#"^\\d+$"#:
                            "number"
                        case regex r#"^[A-Za-z]+$"#:
                            "word"
                        default:
                            "other"
                    }
                }

                println(kind("123"))
                println(kind("abc"))
                println(kind("a1"))
                """)).isEqualTo("number\nword\nother\n");
    }

    @Test
    public void nestedIfAndSwitchExpressions() {
        assertThat(run("""
                func classify(value: Integer, flag: Boolean): String {
                    return if (flag) {
                        switch (value) {
                            case 0:
                                "zero"
                            default:
                                "value"
                        }
                    } else {
                        "off"
                    }
                }

                println(classify(0, true))
                println(classify(5, true))
                println(classify(0, false))
                """)).isEqualTo("zero\nvalue\noff\n");
    }

    @Test
    public void statementFormsKeepTheirOldBehavior() {
        assertThat(run("""
                func run(debug: Boolean, value: Integer): Unit {
                    if (debug) {
                        println("debug")
                    }

                    switch (value) {
                        case 1:
                            println("one")
                    }
                }

                run(true, 1)
                run(true, 9)
                run(false, 1)
                """)).isEqualTo("debug\none\ndebug\none\n");
    }

    @Test
    public void ifAndSwitchCanBeBlockTails() {
        assertThat(run("""
                val fromIf = {
                    if (true) { 1 } else { 2 }
                }
                val fromSwitch = {
                    switch (3) {
                        case 3:
                            "three"
                        default:
                            "other"
                    }
                }
                println(fromIf)
                println(fromSwitch)
                """)).isEqualTo("1\nthree\n");
    }
}
