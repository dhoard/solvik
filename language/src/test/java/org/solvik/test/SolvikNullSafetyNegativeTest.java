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

import java.util.List;
import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * Negative Phase 10 semantic tests (docs/LANGUAGE_SPEC.md sections 5 and 18): a null assigned to a
 * non-null type, a nullable dereference without a check, invalid {@code ??} operands, nullable
 * {@code is}/{@code as} type operands, and a write that invalidates narrowing each produce a
 * source-located diagnostic and no typed result.
 */
public final class SolvikNullSafetyNegativeTest {

    private static final String BOX = """
            class Box {
                val value: Int

                Box(value: Int) {
                    this.value = value
                }
            }
            """;

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("nullneg.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        assertThat(result.program().isEmpty()).as("failed analysis must expose no program").isTrue();
        assertThat(result.diagnostics().hasErrors()).as("failed analysis must carry diagnostics").isTrue();
        for (Diagnostic diagnostic : result.diagnostics().all()) {
            assertThat(diagnostic.span().endOffset() <= text.length()).as("span within source bounds: " + diagnostic.span()).isTrue();
        }
        return result.diagnostics();
    }

    private static Diagnostic first(DiagnosticBag bag) {
        List<Diagnostic> all = bag.all();
        assertThat(all.isEmpty()).isFalse();
        return all.get(0);
    }

    @Test
    public void nullAssignedToANonNullTypeIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x: String = null\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void nullArgumentToANonNullParameterIsRejected() {
        assertThat(first(checkFails("func g(s: String): Unit {\n}\nfunc f(): Unit {\n    g(null)\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void nullReturnFromANonNullFunctionIsRejected() {
        assertThat(first(checkFails("func f(): String {\n    return null\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_RETURN_MISMATCH);
    }

    @Test
    public void nullableToNonNullAssignmentIsRejected() {
        assertThat(first(checkFails("func f(s: String?): Unit {\n    val t: String = s\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void nullableReturnFromANonNullFunctionIsRejected() {
        assertThat(first(checkFails("func f(s: String?): String {\n    return s\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_RETURN_MISMATCH);
    }

    @Test
    public void nullableDereferenceWithoutACheckIsRejected() {
        assertThat(first(checkFails(BOX + "func f(box: Box?): Int {\n    return box.value\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE);
    }

    @Test
    public void nullableMethodCallWithoutACheckIsRejected() {
        String text = """
                class Box {
                    func size(): Int {
                        return 1
                    }
                }
                func f(box: Box?): Int {
                    return box.size()
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE);
    }

    @Test
    public void nullablePropertyWriteWithoutACheckIsRejected() {
        String text = """
                class Box {
                    var value: Int

                    Box(value: Int) {
                        this.value = value
                    }
                }
                func f(box: Box?): Unit {
                    box.value = 1
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE);
    }

    @Test
    public void assignmentThroughASafeAccessIsRejected() {
        String text = """
                class Box {
                    var value: Int

                    Box(value: Int) {
                        this.value = value
                    }
                }
                func f(box: Box?): Unit {
                    box?.value = 1
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET);
    }

    @Test
    public void coalescingANonNullableLeftIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val s: String = \"x\"\n    val y = s ?? \"z\"\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_NULLABLE_REQUIRED);
    }

    @Test
    public void coalescingIncompatibleOperandsIsRejected() {
        assertThat(first(checkFails("func f(s: String?): Int {\n    return s ?? 1\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void nullableTypeTestOperandIsRejected() {
        assertThat(first(checkFails("func f(v: Any): Boolean {\n    return v is String?\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_TYPE_OPERAND);
    }

    @Test
    public void nullableCastOperandIsRejected() {
        assertThat(first(checkFails("func f(v: Any): Unit {\n    val x = v as String?\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_TYPE_OPERAND);
    }

    @Test
    public void aWriteInsideTheCheckedBlockInvalidatesNarrowing() {
        String text = """
                class Box {
                    val value: Int

                    Box(value: Int) {
                        this.value = value
                    }
                }
                func f(): Int {
                    var box: Box? = Box(1)
                    if (box != null) {
                        box = null
                        return box.value
                    }
                    return 0
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE);
    }

    @Test
    public void aWriteInANestedBranchInvalidatesNarrowingAfterTheIf() {
        String text = """
                class Box {
                    val value: Int

                    Box(value: Int) {
                        this.value = value
                    }
                }
                func f(flag: Boolean): Int {
                    var box: Box? = Box(1)
                    if (box != null) {
                        if (flag) {
                            box = null
                        }
                        return box.value
                    }
                    return 0
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE);
    }

    @Test
    public void aWriteInsideALoopInvalidatesNarrowingAfterTheLoop() {
        String text = """
                class Box {
                    val value: Int

                    Box(value: Int) {
                        this.value = value
                    }
                }
                func f(flag: Boolean): Int {
                    var box: Box? = Box(1)
                    if (box != null) {
                        while (flag) {
                            box = null
                        }
                        return box.value
                    }
                    return 0
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE);
    }

    @Test
    public void arithmeticOnANullableIsRejected() {
        assertThat(first(checkFails("func f(a: Int?): Int {\n    return a + 1\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void unknownTypeInANullableAnnotationIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x: Nope? = null\n}\n")).code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }

    @Test
    public void aNonNullableTestDoesNotNarrowToANullableType() {
        // A `?` type operand is rejected even when the value itself is nullable machinery.
        assertThat(first(checkFails("func f(s: String?): Boolean {\n    return s is String?\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_TYPE_OPERAND);
    }
}
