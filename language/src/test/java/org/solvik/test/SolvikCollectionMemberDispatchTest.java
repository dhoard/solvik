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
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.junit.jupiter.api.Test;

/**
 * Member dispatch on a built-in collection receiver (docs/LANGUAGE_SPEC.md sections 3, 7, and 11).
 * The universal {@code Any} members {@code toString} and {@code equals} resolve before any per-type
 * member table, so a statically typed collection receiver must reach them exactly as an {@code Any}
 * receiver does; the collection's own erased member table knows only its declared members and must
 * not shadow the universal ones. Collections compare by reference identity, so {@code equals} on two
 * equal-content collections is false. A nullable collection receiver also resolves its computed
 * members and declared methods, which a safe access reports as {@code null} when the receiver is
 * {@code null}.
 */
public final class SolvikCollectionMemberDispatchTest {

    private static String run(String program) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(Source.newBuilder("solvik", program, "dispatch.sol")));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Source build(Source.Builder builder) {
        try {
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // -------------------------------------------------------------- universal members

    @Test
    public void listReachesToStringFromAStaticallyTypedReceiver() {
        assertThat(run("""
                var nums: List<Integer> = List(1, 2, 3)
                println(nums.toString())
                """)).isEqualTo("List\n");
    }

    @Test
    public void everyCollectionKindReachesToString() {
        assertThat(run("""
                var nums: List<Integer> = List(1)
                var unique: Set<Integer> = Set(1)
                var map: Map<Integer, Integer> = Map(1: 1)
                var stack: Stack<Integer> = Stack(1)
                println(nums.toString())
                println(unique.toString())
                println(map.toString())
                println(stack.toString())
                """)).isEqualTo("List\nSet\nMap\nStack\n");
    }

    @Test
    public void toStringAgreesThroughAnAnyReceiver() {
        assertThat(run("""
                var nums: List<Integer> = List(1)
                var boxed: Any = nums
                println(nums.toString())
                println(boxed.toString())
                """)).isEqualTo("List\nList\n");
    }

    @Test
    public void equalsHoldsForTheSameCollectionReference() {
        assertThat(run("""
                var nums: List<Integer> = List(1, 2, 3)
                println(nums.equals(nums))
                """)).isEqualTo("true\n");
    }

    @Test
    public void equalsIsFalseForDistinctCollectionsWithEqualContent() {
        // Collections are reference-identical (docs/LANGUAGE_SPEC.md section 3), never structural.
        assertThat(run("""
                var left: List<Integer> = List(1, 2, 3)
                var right: List<Integer> = List(1, 2, 3)
                println(left.equals(right))
                println(left == right)
                """)).isEqualTo("false\nfalse\n");
    }

    @Test
    public void equalsIsFalseAcrossDifferentCollectionKinds() {
        // equals takes Any?, so a cross-kind comparison is legal and always false: distinct
        // references, and collections compare by reference identity.
        assertThat(run("""
                var nums: List<Integer> = List(1)
                var unique: Set<Integer> = Set(1)
                println(nums.equals(unique))
                println(unique.equals(nums))
                """)).isEqualTo("false\nfalse\n");
    }

    @Test
    public void universalMembersReflectPostMutationState() {
        // toString and equals read through the same erased receiver the declared members mutate.
        assertThat(run("""
                var nums: List<Integer> = List<Integer>()
                println(nums.toString())
                nums.add(1)
                nums.add(2)
                println(nums.equals(nums))
                println(nums.size)
                """)).isEqualTo("List\ntrue\n2\n");
    }

    @Test
    public void safeUniversalMemberOnANullCollectionYieldsNull() {
        assertThat(run("""
                var nums: List<Integer>? = null
                println(nums?.toString())
                println(nums?.equals(nums))
                """)).isEqualTo("null\nnull\n");
    }

    // -------------------------------------------------------------- declared members

    @Test
    public void declaredMembersStillDispatchAfterTheUniversalMembers() {
        assertThat(run("""
                var nums: List<Integer> = List(1, 2, 3)
                nums.add(4)
                println(nums.get(3))
                println(nums.size)
                println(nums.isEmpty)
                nums.set(0, 9)
                println(nums.get(0))
                """)).isEqualTo("4\n4\nfalse\n9\n");
    }

    @Test
    public void unknownMemberOnACollectionReceiverIsRejected() {
        // The universal members are reachable; a name in neither table is still a compile-time error.
        String text = """
                func f(): Unit {
                    var nums: List<Integer> = List(1)
                    println(nums.bogus())
                }
                """;
        CompilationUnitNode unit = parseOk("dispatch-neg.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        assertThat(result.diagnostics().all().isEmpty()).isFalse();
        Diagnostic first = result.diagnostics().all().get(0);
        assertThat(first.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    // -------------------------------------------------------------- nullable receivers

    @Test
    public void safeComputedSizeOnANullListYieldsNull() {
        assertThat(run("""
                var nums: List<Integer>? = null
                println(nums?.size)
                """)).isEqualTo("null\n");
    }

    @Test
    public void safeComputedMembersResolveOnEveryNullableCollectionKind() {
        assertThat(run("""
                var nums: List<Integer>? = null
                var unique: Set<Integer>? = null
                var map: Map<Integer, Integer>? = null
                var stack: Stack<Integer>? = null
                println(nums?.isEmpty)
                println(unique?.isEmpty)
                println(map?.isEmpty)
                println(stack?.isEmpty)
                """)).isEqualTo("null\nnull\nnull\nnull\n");
    }

    @Test
    public void safeDeclaredMethodOnANullListYieldsNull() {
        assertThat(run("""
                var nums: List<Integer>? = null
                println(nums?.get(0))
                """)).isEqualTo("null\n");
    }

    @Test
    public void nullableCollectionMembersResolveOnceTheReceiverIsPresent() {
        assertThat(run("""
                var nums: List<Integer>? = null
                println(nums?.size)
                nums = List<Integer>(7, 8)
                println(nums?.size)
                println(nums?.get(1))
                """)).isEqualTo("null\n2\n8\n");
    }
}
