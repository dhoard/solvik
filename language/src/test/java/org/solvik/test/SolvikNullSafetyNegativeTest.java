/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.Test;
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
        assertFalse("analysis must fail: " + text, result.isSuccess());
        assertTrue("failed analysis must expose no program", result.program().isEmpty());
        assertTrue("failed analysis must carry diagnostics", result.diagnostics().hasErrors());
        for (Diagnostic diagnostic : result.diagnostics().all()) {
            assertTrue("span within source bounds: " + diagnostic.span(), diagnostic.span().endOffset() <= text.length());
        }
        return result.diagnostics();
    }

    private static Diagnostic first(DiagnosticBag bag) {
        List<Diagnostic> all = bag.all();
        assertFalse(all.isEmpty());
        return all.get(0);
    }

    @Test
    public void nullAssignedToANonNullTypeIsRejected() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails("func f(): Unit {\n    val x: String = null\n}\n")).code());
    }

    @Test
    public void nullArgumentToANonNullParameterIsRejected() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails("func g(s: String): Unit {\n}\nfunc f(): Unit {\n    g(null)\n}\n")).code());
    }

    @Test
    public void nullReturnFromANonNullFunctionIsRejected() {
        assertEquals(DiagnosticCode.TYPE_RETURN_MISMATCH, first(checkFails("func f(): String {\n    return null\n}\n")).code());
    }

    @Test
    public void nullableToNonNullAssignmentIsRejected() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails("func f(s: String?): Unit {\n    val t: String = s\n}\n")).code());
    }

    @Test
    public void nullableReturnFromANonNullFunctionIsRejected() {
        assertEquals(DiagnosticCode.TYPE_RETURN_MISMATCH, first(checkFails("func f(s: String?): String {\n    return s\n}\n")).code());
    }

    @Test
    public void nullableDereferenceWithoutACheckIsRejected() {
        assertEquals(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE, first(checkFails(BOX + "func f(box: Box?): Int {\n    return box.value\n}\n")).code());
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
        assertEquals(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE, first(checkFails(text)).code());
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
        assertEquals(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE, first(checkFails(text)).code());
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
        assertEquals(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, first(checkFails(text)).code());
    }

    @Test
    public void coalescingANonNullableLeftIsRejected() {
        assertEquals(DiagnosticCode.TYPE_NULLABLE_REQUIRED, first(checkFails("func f(): Unit {\n    val s: String = \"x\"\n    val y = s ?? \"z\"\n}\n")).code());
    }

    @Test
    public void coalescingIncompatibleOperandsIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, first(checkFails("func f(s: String?): Int {\n    return s ?? 1\n}\n")).code());
    }

    @Test
    public void nullableTypeTestOperandIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_TYPE_OPERAND, first(checkFails("func f(v: Any): Boolean {\n    return v is String?\n}\n")).code());
    }

    @Test
    public void nullableCastOperandIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_TYPE_OPERAND, first(checkFails("func f(v: Any): Unit {\n    val x = v as String?\n}\n")).code());
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
        assertEquals(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE, first(checkFails(text)).code());
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
        assertEquals(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE, first(checkFails(text)).code());
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
        assertEquals(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE, first(checkFails(text)).code());
    }

    @Test
    public void arithmeticOnANullableIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, first(checkFails("func f(a: Int?): Int {\n    return a + 1\n}\n")).code());
    }

    @Test
    public void unknownTypeInANullableAnnotationIsRejected() {
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_TYPE, first(checkFails("func f(): Unit {\n    val x: Nope? = null\n}\n")).code());
    }

    @Test
    public void aNonNullableTestDoesNotNarrowToANullableType() {
        // A `?` type operand is rejected even when the value itself is nullable machinery.
        assertEquals(DiagnosticCode.TYPE_INVALID_TYPE_OPERAND, first(checkFails("func f(s: String?): Boolean {\n    return s is String?\n}\n")).code());
    }
}
