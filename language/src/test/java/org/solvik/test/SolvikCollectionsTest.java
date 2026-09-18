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
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * Phase 11 collection tests (docs/LANGUAGE_SPEC.md section 11): the four mutable built-in
 * collections {@code List<T>}, {@code Set<T>}, {@code Map<K, V>}, and {@code Stack<T>}. A collection
 * constructs empty through a call with no value arguments ({@code List<Int>()}) or pre-populated
 * from initial elements; {@code Map} takes {@code key: value} entries. Type arguments come from the
 * left-hand declared type or from an explicit type-argument list, then the collection mutates
 * through its members. Positive tests drive real construction and mutation through the polyglot
 * context so lowering and the Truffle runtime are exercised; negative tests assert the diagnostic
 * codes for arity, type, and bounds violations.
 */
public final class SolvikCollectionsTest {

    // ---------------------------------------------------------------- positive execution

    @Test
    public void listAddsAndGetByIndex() {
        assertThat(runMain("""
                var nums: List<Int> = List<Int>()
                nums.add(1)
                nums.add(2)
                nums.add(3)
                nums.add(4)
                nums.set(1, 20)
                println(nums.size)
                println(nums.get(0))
                println(nums.get(1))
                """)).isEqualTo("4\n1\n20\n");
    }

    @Test
    public void listRemoveAtReturnsRemovedElement() {
        assertThat(runMain("""
                var nums: List<Int> = List<Int>()
                nums.add(1)
                nums.add(2)
                nums.add(3)
                var removed = nums.removeAt(1)
                println(nums.size)
                println(removed)
                """)).isEqualTo("2\n2\n");
    }

    @Test
    public void listIsMutable() {
        assertThat(runMain("""
                var nums: List<Int> = List<Int>()
                nums.add(4)
                println(!nums.isEmpty)
                """)).isEqualTo("true\n");
    }

    @Test
    public void setAddsUniqueElements() {
        assertThat(runMain("""
                var s: Set<String> = Set<String>()
                s.add("a")
                s.add("a")
                s.add("b")
                println(s.size)
                println(s.contains("a"))
                println(s.contains("b"))
                """)).isEqualTo("2\ntrue\ntrue\n");
    }

    @Test
    public void setRemoveDropsAMember() {
        assertThat(runMain("""
                var s: Set<String> = Set<String>()
                s.add("x")
                s.add("y")
                var present = s.remove("y")
                println(present)
                println(s.size)
                """)).isEqualTo("true\n1\n");
    }

    @Test
    public void mapPutsAndGetByKey() {
        assertThat(runMain("""
                var m: Map<Int, String> = Map<Int, String>()
                m.put(1, "one")
                m.put(2, "two")
                println(m.get(1))
                println(m.get(2))
                println(m.containsKey(2))
                """)).isEqualTo("one\ntwo\ntrue\n");
    }

    @Test
    public void mapPutReplacesWithoutGrowing() {
        assertThat(runMain("""
                var m: Map<Int, String> = Map<Int, String>()
                m.put(1, "one")
                m.put(1, "one2")
                println(m.size)
                println(m.get(1))
                """)).isEqualTo("1\none2\n");
    }

    @Test
    public void mapRemoveDeletesKey() {
        assertThat(runMain("""
                var m: Map<Int, String> = Map<Int, String>()
                m.put(1, "one")
                m.put(2, "two")
                var present = m.remove(1)
                println(present)
                println(m.size)
                """)).isEqualTo("true\n1\n");
    }

    @Test
    public void mapGetMissingKeyRaisesCollectionError() {
        runtimeFails("""
                func f(): Int {
                    var m: Map<Int, String> = Map<Int, String>()
                    return m.get(9)
                }
                f()
                """);
    }

    @Test
    public void stackPeekAndPopLifo() {
        String out = runMain("""
                var st: Stack<Int> = Stack<Int>()
                st.push(10)
                st.push(20)
                println(st.peek())
                println(st.pop())
                """
        );
        assertThat(out).isEqualTo("20\n20\n");
    }

    @Test
    public void stackPopEmptyRaisesCollectionError() {
        runtimeFails("""
                func f(): Int {
                    var st: Stack<Int> = Stack<Int>()
                    return st.pop()
                }
                f()
                """);
    }

    @Test
    public void collectionClearEmptiesIt() {
        assertThat(runMain("""
                var nums: List<Int> = List<Int>()
                nums.add(1)
                nums.add(2)
                nums.add(3)
                nums.clear()
                println(nums.size)
                """)).isEqualTo("0\n");
    }

    @Test
    public void listConstructorTakesInitialElements() {
        assertThat(runMain("""
                var nums: List<Int> = List(1, 2, 3)
                println(nums.size)
                println(nums.get(0))
                println(nums.get(2))
                """)).isEqualTo("3\n1\n3\n");
    }

    @Test
    public void listConstructorWithExplicitTypeArgumentsTakesInitialElements() {
        assertThat(runMain("""
                var nums = List<Int>(1, 2, 3)
                nums.add(4)
                println(nums.size)
                """)).isEqualTo("4\n");
    }

    @Test
    public void setConstructorKeepsTheFirstOccurrence() {
        assertThat(runMain("""
                var s: Set<Int> = Set(1, 1, 2, 2)
                println(s.size)
                println(s.contains(1))
                """)).isEqualTo("2\ntrue\n");
    }

    @Test
    public void stackConstructorPushesInOrder() {
        assertThat(runMain("""
                var st: Stack<Int> = Stack(10, 20, 30)
                println(st.size)
                println(st.pop())
                """)).isEqualTo("3\n30\n");
    }

    @Test
    public void mapConstructorTakesKeyValueEntries() {
        assertThat(runMain("""
                var m: Map<Int, String> = Map(1: "one", 2: "two")
                println(m.size)
                println(m.get(1))
                println(m.containsKey(2))
                """)).isEqualTo("2\none\ntrue\n");
    }

    @Test
    public void mapConstructorWithExplicitTypeArgumentsTakesEntries() {
        assertThat(runMain("""
                var m = Map<String, Int>("one": 1)
                println(m.size)
                """)).isEqualTo("1\n");
    }

    @Test
    public void mapConstructorAcceptsATrailingComma() {
        assertThat(runMain("""
                var m: Map<Int, String> = Map(1: "one", 2: "two",)
                println(m.size)
                println(m.get(1))
                """)).isEqualTo("2\none\n");
    }

    @Test
    public void mapConstructorKeepsTheLastValueForARepeatedKey() {
        assertThat(runMain("""
                var m: Map<Int, String> = Map(1: "one", 1: "two")
                println(m.size)
                println(m.get(1))
                """)).isEqualTo("1\ntwo\n");
    }

    @Test
    public void nestedCollectionConstructionInfersFromItsElementPosition() {
        assertThat(runMain("""
                var rows: List<List<Int>> = List(List(7))
                println(rows.size)
                println(rows.get(0).get(0))
                """)).isEqualTo("1\n7\n");
    }

    // ------------------------------------------------------------------ negative semantic

    @Test
    public void listElementWithTheWrongTypeIsRejected() {
        assertThat(firstFails("""
                func f(): Unit {
                    var nums: List<Int> = List(1, "two")
                }
                """)).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void mapKeyWithTheWrongTypeIsRejected() {
        assertThat(firstFails("""
                func f(): Unit {
                    var m: Map<Int, String> = Map("one": "one")
                }
                """)).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void mapValueWithTheWrongTypeIsRejected() {
        assertThat(firstFails("""
                func f(): Unit {
                    var m: Map<Int, String> = Map(1: 2)
                }
                """)).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void mapPositionalValueIsRejected() {
        assertThat(firstFails("""
                func f(): Unit {
                    var m: Map<Int, String> = Map(1, "one")
                }
                """)).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void keyValueEntryOutsideAMapConstructionIsRejected() {
        assertThat(firstFails("""
                func f(): Unit {
                    var nums: List<Int> = List(1: 2)
                }
                """)).isEqualTo(DiagnosticCode.SEM_MAP_ENTRY);
    }

    @Test
    public void constructionWithoutLhsOrExplicitTypeArgumentsIsRejected() {
        assertThat(firstFails("""
                func f(): Unit {
                    var nums = List(1, 2)
                }
                """)).isEqualTo(DiagnosticCode.TYPE_CANNOT_INFER);
    }

    @Test
    public void mapConstructionWithoutLhsOrExplicitTypeArgumentsIsRejected() {
        assertThat(firstFails("""
                func f(): Unit {
                    var m = Map(1: "one")
                }
                """)).isEqualTo(DiagnosticCode.TYPE_CANNOT_INFER);
    }

    @Test
    public void listAddWithWrongArityIsRejected() {
        assertThat(firstFails("""
                func f(values: List<Int>): Unit {
                    values.add()
                }
                """)).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void listGetWithNonIntIndexIsRejected() {
        assertThat(firstFails("""
                func f(values: List<Int>): Int {
                    return values.get("x")
                }
                """)).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void listGetOnListIntWithStringReturnIsRejected() {
        assertThat(firstFails("""
                func f(values: List<Int>): String {
                    return values.get(0)
                }
                """)).isEqualTo(DiagnosticCode.TYPE_RETURN_MISMATCH);
    }

    @Test
    public void setSizeIsImmutable() {
        assertThat(firstFails("""
                func f(values: List<Int>): Unit {
                    values.size = 5
                }
                """)).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }

    @Test
    public void unknownCollectionMemberIsRejected() {
        assertThat(firstFails("""
                func f(values: List<Int>): Int {
                    return values.length
                }
                """)).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void unknownCollectionNameIsRejected() {
        assertThat(firstFails("""
                func f(): Int {
                    var v: Queue<Int> = Queue<Int>()
                    return v.size
                }
                """)).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }

    @Test
    public void valueLessCollectionConstructionIsRejected() {
        CompilationUnitNode unit = parseOk("ccol.sol", """
                func f() {
                    var l = List()
                }
                """);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must fail: value-less construction has no evidence for the element type").isFalse();
        assertThat(result.diagnostics().all().size()).isEqualTo(1);
        assertThat(firstError(result.diagnostics().all()).code()).isEqualTo(DiagnosticCode.TYPE_CANNOT_INFER);
    }

    @Test
    public void valueLessConstructionInfersFromDeclaredType() {
        assertThat(runMain("""
                func f() {
                    var l: List<Int> = List()
                    l.add(1)
                    println(l.size)
                }
                f()
                """)).isEqualTo("1\n");
    }

    private static DiagnosticCode firstFails(String source) {
        CompilationUnitNode unit = parseOk("cneg.sol", source);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must fail: " + source).isFalse();
        assertThat(result.program().isEmpty()).as("failed analysis must expose no program").isTrue();
        assertThat(result.diagnostics().hasErrors()).as("failed analysis must carry diagnostics").isTrue();
        for (Diagnostic diagnostic : result.diagnostics().all()) {
            assertThat(diagnostic.span().endOffset() <= source.length()).as("span within source bounds: " + diagnostic.span()).isTrue();
        }
        return firstError(result.diagnostics().all()).code();
    }

    private static void runtimeFails(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotException failure = null;
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(Source.newBuilder("solvik", source, "test.sol")));
        } catch (PolyglotException e) {
            failure = e;
        }
        assertThat(failure).as("runtime must fail: " + source).isNotNull();
    }

    private static Source build(Source.Builder builder) {
        try {
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Diagnostic firstError(java.util.List<Diagnostic> diagnostics) {
        for (Diagnostic diagnostic : diagnostics) {
            if (diagnostic.isError()) {
                return diagnostic;
            }
        }
        throw new AssertionError("no error diagnostic: " + diagnostics);
    }

    private static String runMain(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(Source.newBuilder("solvik", source, "test.sol")));
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
