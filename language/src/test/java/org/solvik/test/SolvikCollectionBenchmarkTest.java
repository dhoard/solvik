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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

/**
 * Behavioral guards for the built-in collection runtimes. The performance characteristics of the
 * collection members are part of their documented contract: {@code List} access and {@code Stack}
 * push/pop are positional operations, while {@code Set} and {@code Map} membership is defined by the
 * spec as an equality search, which docs/ARCHITECTURE.md pins until Solvik specifies a hashability
 * contract. These tests lock the observable consequences of that contract: the exact member
 * behavior of both {@code List} element storages, the equality semantics that a hash table would
 * have to reproduce, and the structural scaling property that distinguishes positional access from
 * an equality scan.
 */
public final class SolvikCollectionBenchmarkTest {

    private static String run(String program) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(Source.newBuilder("solvik", program, "collection_benchmark.sol")));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    /** The message of the runtime failure a program raises, or {@code null} when it completes. */
    private static String runtimeFailure(String program) {
        try (Context context = Context.newBuilder("solvik").out(new ByteArrayOutputStream()).allowAllAccess(true).build()) {
            context.eval(build(Source.newBuilder("solvik", program, "collection_benchmark.sol")));
        } catch (PolyglotException e) {
            return e.getMessage();
        }
        return null;
    }

    private static Source build(Source.Builder builder) {
        try {
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Runs the text of a checked-in benchmark after rewriting its element count. */
    private static String runBenchmark(String name, int elementCount) {
        Path file = benchmarkFile(name);
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8).replace("<N>", Integer.toString(elementCount));
            return run(text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path benchmarkFile(String name) {
        for (Path candidate : List.of(Path.of("benchmarks", name), Path.of("..", "benchmarks", name))) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("benchmark not found: " + name);
    }

    /** The first line of a benchmark run, as an integer. */
    private static int firstLine(String output) {
        return Integer.parseInt(output.lines().findFirst().orElseThrow().trim());
    }

    // ---------------------------------------------------------------- integral list storage

    @Test
    public void integralListPreservesEveryDocumentedListMember() {
        assertThat(run("""
                var nums: List<Integer> = List<Integer>(10, 20, 30)
                println(nums.size)
                println(nums.isEmpty)
                nums.add(40)
                println(nums.get(3))
                println(nums.size)
                nums.set(0, 5)
                println(nums.get(0))
                println(nums.removeAt(0))
                println(nums.size)
                nums.clear()
                println(nums.size)
                println(nums.isEmpty)
                """)).isEqualTo("3\nfalse\n40\n4\n5\n5\n3\n0\ntrue\n");
    }

    @Test
    public void integralListGrowsPastItsInitialStorage() {
        // The primitive storage starts small and must double without losing or reordering elements.
        assertThat(run("""
                var nums: List<Integer> = List<Integer>()
                var i = 0
                while (i < 5000) {
                    nums.add(i)
                    i = i + 1
                }
                println(nums.size)
                println(nums.get(0))
                println(nums.get(4999))
                println(nums.get(2500))
                """)).isEqualTo("5000\n0\n4999\n2500\n");
    }

    @Test
    public void integralListRemoveAtShiftsTheTail() {
        assertThat(run("""
                var nums: List<Integer> = List(1, 2, 3, 4, 5)
                println(nums.removeAt(1))
                println(nums.size)
                println(nums.get(1))
                println(nums.removeAt(2))
                println(nums.get(2))
                println(nums.removeAt(2))
                println(nums.size)
                """)).isEqualTo("2\n4\n3\n4\n5\n5\n2\n");
    }

    @Test
    public void integralListClearsAndRefills() {
        assertThat(run("""
                var nums: List<Integer> = List(1, 2, 3)
                nums.clear()
                nums.add(9)
                nums.add(8)
                println(nums.size)
                println(nums.get(0))
                println(nums.get(1))
                """)).isEqualTo("2\n9\n8\n");
    }

    @Test
    public void integralListBoundsErrorsMatchTheErasedList() {
        // The two storages must fail identically for identical misuse of every positional member.
        assertThat(runtimeFailure("""
                var nums: List<Integer> = List(1, 2, 3)
                println(nums.get(3))
                """)).isEqualTo("bounds error: index 3 is out of range for list of size 3");
        assertThat(runtimeFailure("""
                var nums: List<Integer> = List(1, 2, 3)
                println(nums.removeAt(-1))
                """)).isEqualTo("bounds error: index -1 is out of range for list of size 3");
        assertThat(runtimeFailure("""
                var erased: List<Any> = List(1, 2, 3)
                println(erased.get(3))
                """)).isEqualTo("bounds error: index 3 is out of range for list of size 3");
        assertThat(runtimeFailure("""
                var nums: List<Integer> = List(1, 2, 3)
                nums.set(5, 0)
                """)).isEqualTo("bounds error: index 5 is out of range for list of size 3");
    }

    @Test
    public void integralAndErasedListsExposeTheSameValues() {
        // The element storage is an implementation choice: an element leaves the list as an erased
        // value either way, so an integral list and a structurally identical erased list agree.
        assertThat(run("""
                var integral: List<Integer> = List(1, 2, 3)
                var erased: List<Any> = List(1, 2, 3)
                println(integral.get(0) == erased.get(0))
                println(integral.get(2) == erased.get(2))
                println(integral.size == erased.size)
                println(integral.get(1))
                println(erased.get(1))
                """)).isEqualTo("true\ntrue\ntrue\n2\n2\n");
    }

    // ---------------------------------------------------------------- equality semantics

    @Test
    public void setMembershipUsesValueEqualityForScalars() {
        assertThat(run("""
                var s: Set<Integer> = Set<Integer>()
                println(s.add(7))
                println(s.add(7))
                println(s.contains(7))
                println(s.contains(8))
                println(s.size)
                """)).isEqualTo("true\nfalse\ntrue\nfalse\n1\n");
    }

    @Test
    public void mapKeysUseValueEquality() {
        assertThat(run("""
                var m: Map<Integer, String> = Map<Integer, String>()
                m.put(3, "three")
                println(m.get(3))
                println(m.containsKey(3))
                println(m.size)
                println(m.remove(3))
                println(m.containsKey(3))
                """)).isEqualTo("three\ntrue\n1\ntrue\nfalse\n");
    }

    @Test
    public void collectionsCompareByIdentityNotByContent() {
        assertThat(run("""
                var left: Set<Integer> = Set(1, 2)
                var right: Set<Integer> = Set(1, 2)
                println(left == right)
                println(left.equals(right))
                println(left == left)
                """)).isEqualTo("false\nfalse\ntrue\n");
    }

    @Test
    public void equalityForStringsAndBooleansHoldsAcrossCollections() {
        assertThat(run("""
                var s: Set<String> = Set("a", "b")
                println(s.contains("a"))
                println(s.contains("c"))
                var b: Set<Boolean> = Set(true)
                println(b.contains(true))
                println(b.contains(false))
                """)).isEqualTo("true\nfalse\ntrue\nfalse\n");
    }

    // ---------------------------------------------------------------- stack

    @Test
    public void stackPushPopPeekStayPositional() {
        assertThat(run("""
                var st: Stack<Integer> = Stack<Integer>()
                println(st.isEmpty)
                st.push(1)
                st.push(2)
                st.push(3)
                println(st.peek())
                println(st.pop())
                println(st.size)
                println(st.peek())
                """)).isEqualTo("true\n3\n3\n2\n2\n");
    }

    // ---------------------------------------------------------------- scaling property

    @Test
    public void listAccessScalesSubQuadraticallyInCollectionSize() {
        // Positional access is the documented List contract. Doubling the element count may at worst
        // double the access work; a regression to a per-access scan multiplies it fourfold per doubling.
        // The sizes are deliberately modest: measured in one JVM the linear ratio is ~0.9 because
        // warmup dominates, while an equivalent nested-scan probe measures ~8 at these same sizes.
        // Small sizes also keep a failing run on the order of seconds instead of minutes.
        int small = 5000;
        int large = 20000;
        long smallNanos = timed(() -> runBenchmark("list-scaling.sol", small));
        long largeNanos = timed(() -> runBenchmark("list-scaling.sol", large));
        double ratio = (double) largeNanos / (double) Math.max(1, smallNanos);
        assertThat(ratio).as("List positional access must not grow quadratically (small=%d ns, large=%d ns)", smallNanos, largeNanos)
                        .isLessThan(4.0);
        assertThat(firstLine(runBenchmark("list-scaling.sol", large))).isEqualTo(large);
    }

    private static long timed(java.util.function.Supplier<String> body) {
        body.get(); // warm the interpreter and any per-class initialization
        long start = System.nanoTime();
        body.get();
        return System.nanoTime() - start;
    }
}
