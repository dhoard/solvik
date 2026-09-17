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
 * End-to-end Phase 14 execution tests (docs/LANGUAGE_SPEC.md section 14): the built-in
 * {@code Regex} and {@code RegexMatch} API runs through the Truffle AST backend, including raw-string
 * patterns, capture groups, offset properties, constant-pattern reuse, and runtime regex errors for
 * invalid dynamic patterns.
 */
public final class SolvikRegexExecutionTest {

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
    public void matchesRequiresTheCompleteInput() {
        assertEquals("true\nfalse\nfalse\n", run("""
                    val re = Regex(r#"^\\d+$"#)
                    println(re.matches("12345"))
                    println(re.matches("12a45"))
                    println(re.matches(""))
                """));
    }

    @Test
    public void rawStringPatternsWork() {
        assertEquals("true\nfalse\n", run("""
                    val re = Regex(r#"\\d+\\s+\\w+"#)
                    println(re.matches("42 words"))
                    println(re.matches("words 42"))
                """));
    }

    @Test
    public void findReturnsTheFirstMatchWithOffsetsAndGroups() {
        assertEquals("id-42\n0\n5\n2\nid\n42\n", run("""
                    val re = Regex(r#"(\\w+)-(\\d+)"#)
                    val m = re.find("id-42 rest")
                    if (m != null) {
                        println(m.value)
                        println(m.start)
                        println(m.end)
                        println(m.groupCount)
                        println(m.group(1) ?? "none")
                        println(m.group(2) ?? "none")
                    }
                """));
    }

    @Test
    public void findReturnsNullWhenThereIsNoMatch() {
        assertEquals("true\n", run("""
                    val re = Regex(r#"\\d+"#)
                    val m = re.find("abc")
                    println(m == null)
                """));
    }

    @Test
    public void groupZeroIsTheCompleteMatch() {
        assertEquals("ab-12\n", run("""
                    val m = Regex(r#"(\\w+)-(\\d+)"#).find("ab-12")
                    if (m != null) {
                        println(m.group(0) ?? "none")
                    }
                """));
    }

    @Test
    public void nonParticipatingGroupsAreNull() {
        assertEquals("none\nb\n", run("""
                    val m = Regex(r#"(a)|(b)"#).find("b")
                    if (m != null) {
                        println(m.group(1) ?? "none")
                        println(m.group(2) ?? "none")
                    }
                """));
    }

    @Test
    public void findAllReturnsEveryMatchInSourceOrder() {
        assertEquals("3\n1\n22\n333\n", run("""
                    val re = Regex(r#"\\d+"#)
                    val matches = re.findAll("a1b22c333")
                    println(matches.size)
                    var i = 0
                    while (i < matches.size) {
                        val m: RegexMatch = matches.get(i)
                        println(m.value)
                        i = i + 1
                    }
                """));
    }

    @Test
    public void replaceReplacesAllMatchesAndTreatsTheReplacementAsLiteralText() {
        assertEquals("a#b#c#\na$1b$1\n", run("""
                    val re = Regex(r#"\\d+"#)
                    println(re.replace("a1b22c333", "#"))
                    println(re.replace("a1b2", "$1"))
                """));
    }

    @Test
    public void constantPatternsWorkInsideLoops() {
        assertEquals("3\n", run("""
                    var i = 0
                    var count = 0
                    while (i < 3) {
                        val re = Regex(r#"^\\d+$"#)
                        if (re.matches("123")) {
                            count = count + 1
                        }
                        i = i + 1
                    }
                    println(count)
                """));
    }

    @Test
    public void regexValuesPassThroughFunctions() {
        assertEquals("true\nfalse\n", run("""
                func matchesNumber(re: Regex, value: String): Boolean {
                    return re.matches(value)
                }

                    val re = Regex(r#"\\d+"#)
                    println(matchesNumber(re, "123"))
                    println(matchesNumber(re, "abc"))
                """));
    }

    @Test
    public void safeAccessOnANullableRegexShortCircuits() {
        assertEquals("false\ntrue\n", run("""
                func check(re: Regex?): Boolean? {
                    return re?.matches("a")
                }

                    val none: Regex? = null
                    println(check(none) ?? false)
                    println(check(Regex("a")) ?? false)
                """));
    }

    @Test
    public void regexAndMatchDisplayAsTheirTypeNames() {
        assertEquals("Regex\nRegexMatch\n", run("""
                    println(Regex("a"))
                    val m = Regex("a").find("a")
                    if (m != null) {
                        println(m)
                    }
                """));
    }

    @Test
    public void regexTypeTestsExecute() {
        assertEquals("regex\nmatch\nother\n", run("""
                func kind(value: Any): String {
                    if (value is Regex) {
                        return "regex"
                    }
                    if (value is RegexMatch) {
                        return "match"
                    }
                    return "other"
                }

                    println(kind(Regex("a")))
                    val m = Regex("a").find("a")
                    if (m != null) {
                        println(kind(m))
                    }
                    println(kind("plain"))
                """));
    }

    @Test
    public void anInvalidDynamicPatternRaisesARuntimeRegexError() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            PolyglotException failure = assertThrows(PolyglotException.class, () -> context.eval(build("""
                    func make(): String {
                        return "("
                    }

                        val re = Regex(make())
                        println("after")
                    """, "test.sol")));
            assertEquals(0, out.size());
            assertEquals(true, failure.isGuestException());
        }
    }

    @Test
    public void anUnsupportedDynamicPatternRaisesARuntimeRegexError() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            PolyglotException failure = assertThrows(PolyglotException.class, () -> context.eval(build("""
                    func make(): String {
                        return "(?=x)"
                    }

                        val re = Regex(make())
                        println("after")
                    """, "test.sol")));
            assertEquals(0, out.size());
            assertEquals(true, failure.isGuestException());
        }
    }

    @Test
    public void anOutOfRangeGroupRaisesABoundsError() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            PolyglotException failure = assertThrows(PolyglotException.class, () -> context.eval(build("""
                        val m = Regex(r#"(a)"#).find("a")
                        if (m != null) {
                            println(m.group(5) ?? "none")
                        }
                    """, "test.sol")));
            assertEquals(0, out.size());
            assertEquals(true, failure.isGuestException());
        }
    }
}
