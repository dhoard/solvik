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
 * End-to-end Phase 15 execution tests (docs/LANGUAGE_SPEC.md section 13): first-match source order,
 * grouped and default cases, no implicit fallthrough, full-input regex cases, and the switch-specific
 * {@code break}/{@code continue} behavior.
 */
public final class SolvikSwitchExecutionTest {

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
    public void firstMatchInSourceOrderExecutes() {
        assertThat(run("""
                func label(value: Integer): String {
                    switch (value) {
                        case 1:
                            return "one"
                        case 2:
                            return "two"
                        default:
                            return "other"
                    }
                }

                    println(label(1))
                    println(label(2))
                    println(label(3))
                """)).isEqualTo("one\ntwo\nother\n");
    }

    @Test
    public void groupedCasesShareOneBody() {
        assertThat(run("""
                func classify(value: Integer): String {
                    switch (value) {
                        case 1, 2, 3:
                            return "small"
                        default:
                            return "big"
                    }
                }

                    println(classify(1))
                    println(classify(2))
                    println(classify(3))
                    println(classify(4))
                """)).isEqualTo("small\nsmall\nsmall\nbig\n");
    }

    @Test
    public void defaultRunsWhenNothingMatches() {
        assertThat(run("""
                    switch (99) {
                        case 1:
                            println("one")
                        default:
                            println("other")
                    }
                """)).isEqualTo("other\n");
    }

    @Test
    public void noImplicitFallthrough() {
        assertThat(run("""
                    switch (1) {
                        case 1:
                            println("one")
                        case 2:
                            println("two")
                        default:
                            println("other")
                    }
                """)).isEqualTo("one\n");
    }

    @Test
    public void stringConstantsDispatchByValue() {
        assertThat(run("""
                func name(value: String): String {
                    switch (value) {
                        case "a":
                            return "alpha"
                        case "b":
                            return "beta"
                        default:
                            return "other"
                    }
                }

                    println(name("a"))
                    println(name("b"))
                    println(name("c"))
                """)).isEqualTo("alpha\nbeta\nother\n");
    }

    @Test
    public void nullableStringHandlesTheNullCase() {
        assertThat(run("""
                func describe(value: String?): String {
                    switch (value) {
                        case null:
                            return "none"
                        case "x":
                            return "x"
                        default:
                            return "other"
                    }
                }

                    println(describe(null))
                    println(describe("x"))
                """)).isEqualTo("none\nx\n");
    }

    @Test
    public void regexCaseMatchesTheWholeInput() {
        assertThat(run("""
                func classify(input: String): String {
                    switch (input) {
                        case regex r#"a"#:
                            return "match"
                        default:
                            return "other"
                    }
                }

                    println(classify("a"))
                    println(classify("xax"))
                """)).isEqualTo("match\nother\n");
    }

    /**
     * A `String?` binding narrowed to `String` by a preceding null test is a `String` switch value, so
     * a regex case is accepted and executes on the non-null path while the null path returns earlier
     * (docs/LANGUAGE_SPEC.md sections 5 and 13).
     */
    @Test
    public void regexCaseExecutesOnANullNarrowedScrutinee() {
        assertThat(run("""
                func classify(input: String?): String {
                    if (input == null) {
                        return "missing"
                    }
                    switch (input) {
                        case regex r#"^\\d+$"#:
                            return "number"
                        default:
                            return "other"
                    }
                }

                    println(classify(null))
                    println(classify("123"))
                    println(classify("abc"))
                """)).isEqualTo("missing\nnumber\nother\n");
    }

    @Test
    public void regexCaseDoesNotFallThroughToTheNextCase() {
        assertThat(run("""
                func classify(input: String): String {
                    switch (input) {
                        case regex r#"p"#:
                            return "plain"
                        case regex r#"q"#:
                            return "q"
                        default:
                            return "default"
                    }
                }

                    println(classify("p"))
                    println(classify("z"))
                """)).isEqualTo("plain\ndefault\n");
    }

    @Test
    public void regexCaseAndConstantCaseCoexist() {
        assertThat(run("""
                func classify(input: String): String {
                    switch (input) {
                        case "exact":
                            return "constant"
                        case regex r#"^\\d+$"#:
                            return "regex"
                        default:
                            return "other"
                    }
                }

                    println(classify("exact"))
                    println(classify("42"))
                    println(classify("forty-two"))
                """)).isEqualTo("constant\nregex\nother\n");
    }

    @Test
    public void scrutineeIsEvaluatedExactlyOnce() {
        assertThat(run("""
                class Counter {
                    var count: Integer

                    Counter() {
                        this.count = 0
                    }

                    func next(): Integer {
                        this.count = this.count + 1
                        return this.count
                    }
                }

                    val counter = Counter()
                    switch (counter.next()) {
                        case 1:
                            println("one")
                        default:
                            println("other")
                    }
                    println(counter.count)
                """)).isEqualTo("one\n1\n");
    }

    @Test
    public void breakInsideALoopNestedInACase() {
        assertThat(run("""
                    var total = 0
                    switch (1) {
                        case 1:
                            for (var i = 0; i < 5; i = i + 1) {
                                if (i == 2) {
                                    break
                                }
                                total = total + i
                            }
                        default:
                            total = 0 - 1
                    }
                    println(total)
                """)).isEqualTo("1\n");
    }

    @Test
    public void continueInsideACaseTargetsTheEnclosingLoop() {
        assertThat(run("""
                    for (var i = 0; i < 3; i = i + 1) {
                        switch (i) {
                            case 1:
                                continue
                            default:
                                println(i)
                        }
                    }
                """)).isEqualTo("0\n2\n");
    }

    @Test
    public void nestedSwitchSelectsTheInnerMatch() {
        assertThat(run("""
                func classify(a: Integer, b: Integer): String {
                    switch (a) {
                        case 1:
                            switch (b) {
                                case 1:
                                    return "inner-one"
                                default:
                                    return "inner-other"
                            }
                        default:
                            return "outer"
                    }
                }

                    println(classify(1, 1))
                    println(classify(2, 1))
                """)).isEqualTo("inner-one\nouter\n");
    }

    @Test
    public void anUnmatchedSwitchWithoutADefaultDoesNothing() {
        assertThat(run("""
                    switch (99) {
                        case 1:
                            println("one")
                    }
                    println("after")
                """)).isEqualTo("after\n");
    }
}
