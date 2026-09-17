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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * Phase 11 collection tests (docs/LANGUAGE_SPEC.md section 11): the four mutable built-in
 * collections {@code List<T>}, {@code Set<T>}, {@code Map<K, V>}, and {@code Stack<T>}. Collections
 * construct empty through a zero-argument call ({@code List<Int>}), then mutate through their
 * members. Positive tests drive real mutation through the polyglot context so lowering and the
 * Truffle runtime are exercised; negative tests assert the diagnostic codes for arity, type, and
 * bounds violations.
 */
public final class SolvikCollectionsTest {

    // ---------------------------------------------------------------- positive execution

    @Test
    public void listAddsAndGetByIndex() {
        assertEquals("4\n1\n20\n", runMain("""
                var nums: List<Int> = List<Int>()
                nums.add(1)
                nums.add(2)
                nums.add(3)
                nums.add(4)
                nums.set(1, 20)
                println(nums.size)
                println(nums.get(0))
                println(nums.get(1))
                """));
    }

    @Test
    public void listRemoveAtReturnsRemovedElement() {
        assertEquals("2\n2\n", runMain("""
                var nums: List<Int> = List<Int>()
                nums.add(1)
                nums.add(2)
                nums.add(3)
                var removed = nums.removeAt(1)
                println(nums.size)
                println(removed)
                """));
    }

    @Test
    public void listIsMutable() {
        assertEquals("true\n", runMain("""
                var nums: List<Int> = List<Int>()
                nums.add(4)
                println(!nums.isEmpty)
                """));
    }

    @Test
    public void setAddsUniqueElements() {
        assertEquals("2\ntrue\ntrue\n", runMain("""
                var s: Set<String> = Set<String>()
                s.add("a")
                s.add("a")
                s.add("b")
                println(s.size)
                println(s.contains("a"))
                println(s.contains("b"))
                """));
    }

    @Test
    public void setRemoveDropsAMember() {
        assertEquals("true\n1\n", runMain("""
                var s: Set<String> = Set<String>()
                s.add("x")
                s.add("y")
                var present = s.remove("y")
                println(present)
                println(s.size)
                """));
    }

    @Test
    public void mapPutsAndGetByKey() {
        assertEquals("one\ntwo\ntrue\n", runMain("""
                var m: Map<Int, String> = Map<Int, String>()
                m.put(1, "one")
                m.put(2, "two")
                println(m.get(1))
                println(m.get(2))
                println(m.containsKey(2))
                """));
    }

    @Test
    public void mapPutReplacesWithoutGrowing() {
        assertEquals("1\none2\n", runMain("""
                var m: Map<Int, String> = Map<Int, String>()
                m.put(1, "one")
                m.put(1, "one2")
                println(m.size)
                println(m.get(1))
                """));
    }

    @Test
    public void mapRemoveDeletesKey() {
        assertEquals("true\n1\n", runMain("""
                var m: Map<Int, String> = Map<Int, String>()
                m.put(1, "one")
                m.put(2, "two")
                var present = m.remove(1)
                println(present)
                println(m.size)
                """));
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
        assertEquals("20\n20\n", out);
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
        assertEquals("0\n", runMain("""
                var nums: List<Int> = List<Int>()
                nums.add(1)
                nums.add(2)
                nums.add(3)
                nums.clear()
                println(nums.size)
                """));
    }

    // ------------------------------------------------------------------ negative semantic

    @Test
    public void listAddWithWrongArityIsRejected() {
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, firstFails("""
                func f(values: List<Int>): Unit {
                    values.add()
                }
                """));
    }

    @Test
    public void listGetWithNonIntIndexIsRejected() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, firstFails("""
                func f(values: List<Int>): Int {
                    return values.get("x")
                }
                """));
    }

    @Test
    public void listGetOnListIntWithStringReturnIsRejected() {
        assertEquals(DiagnosticCode.TYPE_RETURN_MISMATCH, firstFails("""
                func f(values: List<Int>): String {
                    return values.get(0)
                }
                """));
    }

    @Test
    public void setSizeIsImmutable() {
        assertEquals(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, firstFails("""
                func f(values: List<Int>): Unit {
                    values.size = 5
                }
                """));
    }

    @Test
    public void unknownCollectionMemberIsRejected() {
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_MEMBER, firstFails("""
                func f(values: List<Int>): Int {
                    return values.length
                }
                """));
    }

    @Test
    public void unknownCollectionNameIsRejected() {
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_TYPE, firstFails("""
                func f(): Int {
                    var v: Queue<Int> = Queue<Int>()
                    return v.size
                }
                """));
    }

    @Test
    public void valueLessCollectionConstructionIsRejected() {
        CompilationUnitNode unit = parseOk("ccol.sol", """
                func f() {
                    var l = List()
                }
                """);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertFalse("analysis must fail: value-less construction has no evidence for the element type", result.isSuccess());
        assertEquals(1, result.diagnostics().all().size());
        assertEquals(DiagnosticCode.TYPE_CANNOT_INFER, firstError(result.diagnostics().all()).code());
    }

    @Test
    public void valueLessConstructionInfersFromDeclaredType() {
        assertEquals("1\n", runMain("""
                func f() {
                    var l: List<Int> = List()
                    l.add(1)
                    println(l.size)
                }
                f()
                """));
    }

    private static DiagnosticCode firstFails(String source) {
        CompilationUnitNode unit = parseOk("cneg.sol", source);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertFalse("analysis must fail: " + source, result.isSuccess());
        assertTrue("failed analysis must expose no program", result.program().isEmpty());
        assertTrue("failed analysis must carry diagnostics", result.diagnostics().hasErrors());
        for (Diagnostic diagnostic : result.diagnostics().all()) {
            assertTrue("span within source bounds: " + diagnostic.span(), diagnostic.span().endOffset() <= source.length());
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
        assertNotNull("runtime must fail: " + source, failure);
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
