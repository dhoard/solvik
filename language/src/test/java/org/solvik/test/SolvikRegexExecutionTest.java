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
import static org.solvik.test.SolvikTestSupport.expectThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

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
        assertThat(run("""
                    val re = Regex(r#"^\\d+$"#)
                    println(re.matches("12345"))
                    println(re.matches("12a45"))
                    println(re.matches(""))
                """)).isEqualTo("true\nfalse\nfalse\n");
    }

    @Test
    public void rawStringPatternsWork() {
        assertThat(run("""
                    val re = Regex(r#"\\d+\\s+\\w+"#)
                    println(re.matches("42 words"))
                    println(re.matches("words 42"))
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void dynamicallyTypedPatternsAreCachedAcrossCalls() {
        assertThat(run("""
                    func countMatches(pattern: String): Integer {
                        val re = Regex(pattern)
                        return re.findAll("a a a").size
                    }

                    println(countMatches("a"))
                    println(countMatches("a"))
                    println(countMatches("b"))
                """)).isEqualTo("3\n3\n0\n");
    }

    @Test
    public void findReturnsTheFirstMatchWithOffsetsAndGroups() {
        assertThat(run("""
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
                """)).isEqualTo("id-42\n0\n5\n2\nid\n42\n");
    }

    @Test
    public void findReturnsNullWhenThereIsNoMatch() {
        assertThat(run("""
                    val re = Regex(r#"\\d+"#)
                    val m = re.find("abc")
                    println(m == null)
                """)).isEqualTo("true\n");
    }

    @Test
    public void groupZeroIsTheCompleteMatch() {
        assertThat(run("""
                    val m = Regex(r#"(\\w+)-(\\d+)"#).find("ab-12")
                    if (m != null) {
                        println(m.group(0) ?? "none")
                    }
                """)).isEqualTo("ab-12\n");
    }

    @Test
    public void nonParticipatingGroupsAreNull() {
        assertThat(run("""
                    val m = Regex(r#"(a)|(b)"#).find("b")
                    if (m != null) {
                        println(m.group(1) ?? "none")
                        println(m.group(2) ?? "none")
                    }
                """)).isEqualTo("none\nb\n");
    }

    @Test
    public void findAllReturnsEveryMatchInSourceOrder() {
        assertThat(run("""
                    val re = Regex(r#"\\d+"#)
                    val matches = re.findAll("a1b22c333")
                    println(matches.size)
                    var i = 0
                    while (i < matches.size) {
                        val m: RegexMatch = matches.get(i)
                        println(m.value)
                        i = i + 1
                    }
                """)).isEqualTo("3\n1\n22\n333\n");
    }

    @Test
    public void replaceReplacesAllMatchesAndTreatsTheReplacementAsLiteralText() {
        assertThat(run("""
                    val re = Regex(r#"\\d+"#)
                    println(re.replace("a1b22c333", "#"))
                    println(re.replace("a1b2", "$1"))
                """)).isEqualTo("a#b#c#\na$1b$1\n");
    }

    @Test
    public void constantPatternsWorkInsideLoops() {
        assertThat(run("""
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
                """)).isEqualTo("3\n");
    }

    @Test
    public void regexValuesPassThroughFunctions() {
        assertThat(run("""
                func matchesNumber(re: Regex, value: String): Boolean {
                    return re.matches(value)
                }

                    val re = Regex(r#"\\d+"#)
                    println(matchesNumber(re, "123"))
                    println(matchesNumber(re, "abc"))
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void safeAccessOnANullableRegexShortCircuits() {
        assertThat(run("""
                func check(re: Regex?): Boolean? {
                    return re?.matches("a")
                }

                    val none: Regex? = null
                    println(check(none) ?? false)
                    println(check(Regex("a")) ?? false)
                """)).isEqualTo("false\ntrue\n");
    }

    @Test
    public void regexAndMatchDisplayAsTheirTypeNames() {
        assertThat(run("""
                    println(Regex("a"))
                    val m = Regex("a").find("a")
                    if (m != null) {
                        println(m)
                    }
                """)).isEqualTo("Regex\nRegexMatch\n");
    }

    @Test
    public void regexTypeTestsExecute() {
        assertThat(run("""
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
                """)).isEqualTo("regex\nmatch\nother\n");
    }

    @Test
    public void anInvalidDynamicPatternRaisesARuntimeRegexError() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            PolyglotException failure = expectThrows(PolyglotException.class, () -> context.eval(build("""
                    func make(): String {
                        return "("
                    }

                        val re = Regex(make())
                        println("after")
                    """, "test.sol")));
            assertThat(out.size()).isEqualTo(0);
            assertThat(failure.isGuestException()).isEqualTo(true);
        }
    }

    @Test
    public void anUnsupportedDynamicPatternRaisesARuntimeRegexError() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            PolyglotException failure = expectThrows(PolyglotException.class, () -> context.eval(build("""
                    func make(): String {
                        return "(?=x)"
                    }

                        val re = Regex(make())
                        println("after")
                    """, "test.sol")));
            assertThat(out.size()).isEqualTo(0);
            assertThat(failure.isGuestException()).isEqualTo(true);
        }
    }

    @Test
    public void anOutOfRangeGroupRaisesABoundsError() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            PolyglotException failure = expectThrows(PolyglotException.class, () -> context.eval(build("""
                        val m = Regex(r#"(a)"#).find("a")
                        if (m != null) {
                            println(m.group(5) ?? "none")
                        }
                    """, "test.sol")));
            assertThat(out.size()).isEqualTo(0);
            assertThat(failure.isGuestException()).isEqualTo(true);
        }
    }
}
