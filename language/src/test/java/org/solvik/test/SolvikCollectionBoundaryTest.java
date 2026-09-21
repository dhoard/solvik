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
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Boundary and error behavior for the built-in mutable collections (docs/LANGUAGE_SPEC.md section
 * 11). The member methods report presence with a boolean, {@code clear} empties any collection,
 * {@code isEmpty}/{@code size} track the current contents, and an invalid list index raises a Solvik
 * bounds error rather than exposing a host exception.
 */
public final class SolvikCollectionBoundaryTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "var l: List<Integer> = List(1); println(l.get(1)) | 1",
            "var l: List<Integer> = List(1); println(l.get(-1)) | -1",
            "var l: List<Integer> = List(1); l.set(1, 5) | 1",
            "var l: List<Integer> = List(1); println(l.removeAt(2)) | 2",
            "var l: List<Integer> = List(); println(l.get(0)) | 0",
            "var l: List<Integer> = List(); l.set(0, 5) | 0",
            "var l: List<Integer> = List(); println(l.removeAt(0)) | 0",
    })
    public void outOfRangeListIndexRaisesABoundsError(String program, int index) {
        PolyglotException failure = failureOf("func f() {\n    " + program + "\n}\nf()\n");
        assertThat(failure.isSyntaxError()).as(failure.getMessage()).isFalse();
        assertThat(failure.getMessage()).contains("bounds error");
        assertThat(failure.getMessage()).contains("index " + index);
    }

    @Test
    public void listIsEmptyAndSizeTrackItsContents() {
        assertThat(run("""
                    var l: List<Integer> = List()
                    println(l.isEmpty)
                    println(l.size)
                    l.add(1)
                    println(l.isEmpty)
                    println(l.size)
                    l.clear()
                    println(l.isEmpty)
                    println(l.size)
                """)).isEqualTo("true\n0\nfalse\n1\ntrue\n0\n");
    }

    @Test
    public void setReportsMembershipAndRemovalPresence() {
        assertThat(run("""
                    var s: Set<Integer> = Set()
                    println(s.add(1))
                    println(s.add(1))
                    println(s.contains(1))
                    println(s.contains(2))
                    println(s.remove(2))
                    println(s.remove(1))
                    println(s.isEmpty)
                """)).isEqualTo("true\nfalse\ntrue\nfalse\nfalse\ntrue\ntrue\n");
    }

    @Test
    public void mapReportsKeyPresenceAndRemovalPresence() {
        assertThat(run("""
                    var m: Map<Integer, Integer> = Map(1: 10)
                    println(m.containsKey(1))
                    println(m.containsKey(2))
                    println(m.remove(2))
                    println(m.remove(1))
                    println(m.isEmpty)
                    println(m.size)
                """)).isEqualTo("true\nfalse\nfalse\ntrue\ntrue\n0\n");
    }

    @Test
    public void stackIsEmptyAndSizeTrackItsContents() {
        assertThat(run("""
                    var st: Stack<Integer> = Stack()
                    println(st.isEmpty)
                    println(st.size)
                    st.push(1)
                    println(st.isEmpty)
                    println(st.size)
                    st.clear()
                    println(st.isEmpty)
                    println(st.size)
                """)).isEqualTo("true\n0\nfalse\n1\ntrue\n0\n");
    }

    @Test
    public void listClearResetsToAnEmptyCollection() {
        assertThat(run("""
                    var l: List<Integer> = List(1, 2, 3)
                    println(l.size)
                    l.clear()
                    println(l.size)
                    println(l.isEmpty)
                """)).isEqualTo("3\n0\ntrue\n");
    }

    @Test
    public void setClearResetsToAnEmptyCollection() {
        assertThat(run("""
                    var s: Set<Integer> = Set(1, 2, 3)
                    println(s.size)
                    s.clear()
                    println(s.size)
                    println(s.isEmpty)
                """)).isEqualTo("3\n0\ntrue\n");
    }

    @Test
    public void mapClearResetsToAnEmptyCollection() {
        assertThat(run("""
                    var m: Map<Integer, Integer> = Map(1: 1, 2: 2)
                    println(m.size)
                    m.clear()
                    println(m.size)
                    println(m.isEmpty)
                """)).isEqualTo("2\n0\ntrue\n");
    }

    @Test
    public void collectionsDisplayAsTheirTypeNames() {
        assertThat(run("""
                    println(List<Integer>(1, 2))
                    println(Set<Integer>(1, 2))
                    println(Map<Integer, Integer>(1: 2))
                    println(Stack<Integer>(1, 2))
                """)).isEqualTo("List\nSet\nMap\nStack\n");
    }

    private static PolyglotException failureOf(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source));
        } catch (PolyglotException e) {
            return e;
        }
        throw new AssertionError("expected a failure but the program completed: " + source);
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
            return Source.newBuilder("solvik", source, "collection-boundary.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
