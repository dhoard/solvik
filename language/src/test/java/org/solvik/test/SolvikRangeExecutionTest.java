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
 * End-to-end range {@code for}-in execution tests (docs/LANGUAGE_SPEC.md section 17): the three
 * operators, empty and reversed ranges, single-element ranges, the {@code Int.MAX_VALUE} boundary,
 * {@code break}/{@code continue}, nesting, single evaluation of bounds, and scoping.
 */
public final class SolvikRangeExecutionTest {

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source, "range.sol"));
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
    public void inclusiveRangeIteratesBothEnds() {
        assertThat(run("""
                var text = ""
                for (i in 1...5) {
                    text = text .. i
                }
                println(text)
                """)).isEqualTo("12345\n");
    }

    @Test
    public void ascendingExclusiveRangeExcludesTheEnd() {
        assertThat(run("""
                var text = ""
                for (i in 0..<4) {
                    text = text .. i
                }
                println(text)
                """)).isEqualTo("0123\n");
    }

    @Test
    public void descendingExclusiveRangeExcludesTheEnd() {
        assertThat(run("""
                var text = ""
                for (i in 5..>0) {
                    text = text .. i
                }
                println(text)
                """)).isEqualTo("54321\n");
    }

    @Test
    public void emptyAndReversedRangesRunZeroTimes() {
        assertThat(run("""
                var count = 0
                for (i in 0..<0) {
                    count = count + 1
                }
                for (j in 5...1) {
                    count = count + 1
                }
                for (k in 0..>0) {
                    count = count + 1
                }
                println(count)
                """)).isEqualTo("0\n");
    }

    @Test
    public void singleElementRangesRunOnce() {
        assertThat(run("""
                var text = ""
                for (i in 3...3) {
                    text = text .. i
                }
                text = text .. "\\n"
                for (j in 3..<4) {
                    text = text .. j
                }
                text = text .. "\\n"
                for (k in 3..>2) {
                    text = text .. k
                }
                println(text)
                """)).isEqualTo("3\n3\n3\n");
    }

    @Test
    public void inclusiveRangeAtIntMaxDoesNotOverflow() {
        assertThat(run("""
                var last = 0
                for (i in 2147483647...2147483647) {
                    last = i
                }
                println(last)
                """)).isEqualTo("2147483647\n");
    }

    @Test
    public void breakAndContinueControlTheLoop() {
        assertThat(run("""
                var total = 0
                for (i in 1...10) {
                    if (i == 3) {
                        continue
                    }
                    if (i > 5) {
                        break
                    }
                    total = total + i
                }
                println(total)
                """)).isEqualTo("12\n");
    }

    @Test
    public void nestedRangeLoopsCountEveryPair() {
        assertThat(run("""
                var pairs = 0
                for (i in 1...2) {
                    for (j in 1...2) {
                        pairs = pairs + 1
                    }
                }
                println(pairs)
                """)).isEqualTo("4\n");
    }

    @Test
    public void boundsAreEvaluatedOnce() {
        assertThat(run("""
                func bound(): Int {
                    print("b")
                    return 3
                }

                var text = ""
                for (i in 1...bound()) {
                    text = text .. i
                }
                println(text)
                """)).isEqualTo("b123\n");
    }

    @Test
    public void negativeBoundsAreSupported() {
        assertThat(run("""
                var text = ""
                for (i in -2...0) {
                    text = text .. i
                }
                println(text)
                """)).isEqualTo("-2-10\n");
    }

    @Test
    public void loopVariableDoesNotEscapeItsScope() {
        assertThat(run("""
                var i = 99
                for (i in 1...1) {
                }
                println(i)
                """)).isEqualTo("99\n");
    }
}
