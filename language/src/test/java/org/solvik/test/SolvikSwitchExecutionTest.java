/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
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
        assertEquals("one\ntwo\nother\n", run("""
                fun label(value: Int): String {
                    switch (value) {
                        case 1:
                            return "one"
                        case 2:
                            return "two"
                        default:
                            return "other"
                    }
                }

                fun main(): Unit {
                    println(label(1))
                    println(label(2))
                    println(label(3))
                }
                """));
    }

    @Test
    public void groupedCasesShareOneBody() {
        assertEquals("small\nsmall\nsmall\nbig\n", run("""
                fun classify(value: Int): String {
                    switch (value) {
                        case 1, 2, 3:
                            return "small"
                        default:
                            return "big"
                    }
                }

                fun main(): Unit {
                    println(classify(1))
                    println(classify(2))
                    println(classify(3))
                    println(classify(4))
                }
                """));
    }

    @Test
    public void defaultRunsWhenNothingMatches() {
        assertEquals("other\n", run("""
                fun main(): Unit {
                    switch (99) {
                        case 1:
                            println("one")
                        default:
                            println("other")
                    }
                }
                """));
    }

    @Test
    public void noImplicitFallthrough() {
        assertEquals("one\n", run("""
                fun main(): Unit {
                    switch (1) {
                        case 1:
                            println("one")
                        case 2:
                            println("two")
                        default:
                            println("other")
                    }
                }
                """));
    }

    @Test
    public void stringConstantsDispatchByValue() {
        assertEquals("alpha\nbeta\nother\n", run("""
                fun name(value: String): String {
                    switch (value) {
                        case "a":
                            return "alpha"
                        case "b":
                            return "beta"
                        default:
                            return "other"
                    }
                }

                fun main(): Unit {
                    println(name("a"))
                    println(name("b"))
                    println(name("c"))
                }
                """));
    }

    @Test
    public void nullableStringHandlesTheNullCase() {
        assertEquals("none\nx\n", run("""
                fun describe(value: String?): String {
                    switch (value) {
                        case null:
                            return "none"
                        case "x":
                            return "x"
                        default:
                            return "other"
                    }
                }

                fun main(): Unit {
                    println(describe(null))
                    println(describe("x"))
                }
                """));
    }

    @Test
    public void regexCaseMatchesTheWholeInput() {
        assertEquals("match\nother\n", run("""
                fun classify(input: String): String {
                    switch (input) {
                        case regex r#"a"#:
                            return "match"
                        default:
                            return "other"
                    }
                }

                fun main(): Unit {
                    println(classify("a"))
                    println(classify("xax"))
                }
                """));
    }

    @Test
    public void regexCaseDoesNotFallThroughToTheNextCase() {
        assertEquals("plain\ndefault\n", run("""
                fun classify(input: String): String {
                    switch (input) {
                        case regex r#"p"#:
                            return "plain"
                        case regex r#"q"#:
                            return "q"
                        default:
                            return "default"
                    }
                }

                fun main(): Unit {
                    println(classify("p"))
                    println(classify("z"))
                }
                """));
    }

    @Test
    public void regexCaseAndConstantCaseCoexist() {
        assertEquals("constant\nregex\nother\n", run("""
                fun classify(input: String): String {
                    switch (input) {
                        case "exact":
                            return "constant"
                        case regex r#"^\\d+$"#:
                            return "regex"
                        default:
                            return "other"
                    }
                }

                fun main(): Unit {
                    println(classify("exact"))
                    println(classify("42"))
                    println(classify("forty-two"))
                }
                """));
    }

    @Test
    public void scrutineeIsEvaluatedExactlyOnce() {
        assertEquals("one\n1\n", run("""
                class Counter {
                    var count: Int

                    init() {
                        this.count = 0
                    }

                    fun next(): Int {
                        this.count = this.count + 1
                        return this.count
                    }
                }

                fun main(): Unit {
                    val counter = Counter()
                    switch (counter.next()) {
                        case 1:
                            println("one")
                        default:
                            println("other")
                    }
                    println(counter.count)
                }
                """));
    }

    @Test
    public void breakInsideALoopNestedInACase() {
        assertEquals("1\n", run("""
                fun main(): Unit {
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
                }
                """));
    }

    @Test
    public void continueInsideACaseTargetsTheEnclosingLoop() {
        assertEquals("0\n2\n", run("""
                fun main(): Unit {
                    for (var i = 0; i < 3; i = i + 1) {
                        switch (i) {
                            case 1:
                                continue
                            default:
                                println(i)
                        }
                    }
                }
                """));
    }

    @Test
    public void nestedSwitchSelectsTheInnerMatch() {
        assertEquals("inner-one\nouter\n", run("""
                fun classify(a: Int, b: Int): String {
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

                fun main(): Unit {
                    println(classify(1, 1))
                    println(classify(2, 1))
                }
                """));
    }

    @Test
    public void anUnmatchedSwitchWithoutADefaultDoesNothing() {
        assertEquals("after\n", run("""
                fun main(): Unit {
                    switch (99) {
                        case 1:
                            println("one")
                    }
                    println("after")
                }
                """));
    }
}
