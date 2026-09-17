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
 * Negative Phase 11 semantic tests (docs/LANGUAGE_SPEC.md section 11): raw generic types, type
 * argument arity and application errors, invariance violations, unbound inference, erased type tests
 * and casts, and generic interface conformance failures.
 */
public final class SolvikGenericsNegativeTest {

    private static final String BOX = """
            class Box<T> {
                var value: T

                init(value: T) {
                    this.value = value
                }
            }
            """;

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("gneg.sol", text);
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
    public void rawGenericTypeIsRejected() {
        assertEquals(DiagnosticCode.TYPE_RAW_GENERIC_TYPE, first(checkFails(BOX + """
                fun f(): Unit {
                    val box: Box = Box(5)
                }
                """)).code());
    }

    @Test
    public void rawBuiltinListIsRejected() {
        assertEquals(DiagnosticCode.TYPE_RAW_GENERIC_TYPE, first(checkFails("""
                fun f(values: List): Unit {
                }
                """)).code());
    }

    @Test
    public void wrongTypeArgumentCountIsRejected() {
        assertEquals(DiagnosticCode.TYPE_TYPE_ARGUMENT_ARITY, first(checkFails(BOX + """
                fun f(): Unit {
                    val box: Box<Int, String> = Box(5)
                }
                """)).code());
    }

    @Test
    public void typeArgumentsOnANonGenericTypeAreRejected() {
        assertEquals(DiagnosticCode.TYPE_NOT_GENERIC, first(checkFails("""
                fun f(): Unit {
                    val x: Int<String> = 5
                }
                """)).code());
    }

    @Test
    public void unknownTypeArgumentIsRejected() {
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_TYPE, first(checkFails(BOX + """
                fun f(): Unit {
                    val box: Box<Widget> = Box(5)
                }
                """)).code());
    }

    @Test
    public void aTypeParameterCannotTakeTypeArguments() {
        assertEquals(DiagnosticCode.TYPE_NOT_GENERIC, first(checkFails("""
                fun f<T>(x: T<Int>): Unit {
                }
                """)).code());
    }

    @Test
    public void typeArgumentsAreInvariant() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails(BOX + """
                fun f(): Unit {
                    val box: Box<Int> = Box("x")
                }
                """)).code());
    }

    @Test
    public void typeArgumentsAreNotCovariant() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails(BOX + """
                fun f(): Unit {
                    val box: Box<Any> = Box("x")
                }
                """)).code());
    }

    @Test
    public void inferredTypeArgumentMustMatchTheCallContext() {
        assertEquals(DiagnosticCode.TYPE_RETURN_MISMATCH, first(checkFails("""
                fun identity<T>(x: T): T {
                    return x
                }
                fun f(): String {
                    return identity(5)
                }
                """)).code());
    }

    @Test
    public void uninferableTypeArgumentIsRejected() {
        assertEquals(DiagnosticCode.TYPE_CANNOT_INFER, first(checkFails(BOX + """
                fun unwrap<T>(box: Box<T>): T {
                    return box.value
                }
                fun f(): Int {
                    return unwrap(5)
                }
                """)).code());
    }

    @Test
    public void duplicateTypeParameterNamesAreRejected() {
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, first(checkFails("""
                class Pair<T, T> {
                    var value: T

                    init(value: T) {
                        this.value = value
                    }
                }
                """)).code());
    }

    @Test
    public void typeTestAgainstAnErasedTypeArgumentIsRejected() {
        assertEquals(DiagnosticCode.TYPE_ERASED_TYPE_TEST, first(checkFails(BOX + """
                fun f(value: Any): Boolean {
                    return (value is Box<String>)
                }
                """)).code());
    }

    @Test
    public void castAgainstAnErasedTypeArgumentIsRejected() {
        assertEquals(DiagnosticCode.TYPE_ERASED_TYPE_TEST, first(checkFails(BOX + """
                fun f(value: Any): Box<String> {
                    return (value as Box<String>)
                }
                """)).code());
    }

    @Test
    public void listSizeIsImmutable() {
        assertEquals(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, first(checkFails("""
                fun f(values: List<Int>): Unit {
                    values.size = 5
                }
                """)).code());
    }

    @Test
    public void listGetRequiresAnIntIndex() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails("""
                fun f(values: List<Int>): Int {
                    return values.get("x")
                }
                """)).code());
    }

    @Test
    public void listElementTypeIsEnforced() {
        assertEquals(DiagnosticCode.TYPE_RETURN_MISMATCH, first(checkFails("""
                fun f(values: List<Int>): String {
                    return values.get(0)
                }
                """)).code());
    }

    @Test
    public void listIsInvariantInItsElementType() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails("""
                fun f(values: List<String>): Unit {
                    val ints: List<Int> = values
                }
                """)).code());
    }

    @Test
    public void listHasNoUnknownMembers() {
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_MEMBER, first(checkFails("""
                fun f(values: List<Int>): Int {
                    return values.length
                }
                """)).code());
    }

    @Test
    public void genericInterfaceRequirementMustBeImplemented() {
        assertEquals(DiagnosticCode.SEM_MISSING_INTERFACE_IMPLEMENTATION, first(checkFails("""
                interface Container<T> {
                    fun get(): T
                }
                class StringBox implements Container<String> {
                }
                """)).code());
    }

    @Test
    public void genericInterfaceImplementationReturnTypeIsChecked() {
        assertEquals(DiagnosticCode.SEM_IMPLEMENTATION_SIGNATURE, first(checkFails("""
                interface Container<T> {
                    fun get(): T
                }
                class StringBox implements Container<String> {
                    fun get(): Int {
                        return 1
                    }
                }
                """)).code());
    }

    @Test
    public void genericInterfaceImplementationParameterTypeIsChecked() {
        assertEquals(DiagnosticCode.SEM_IMPLEMENTATION_SIGNATURE, first(checkFails("""
                interface Consumer<T> {
                    fun accept(value: T): Unit
                }
                class StringConsumer implements Consumer<String> {
                    fun accept(value: Int): Unit {
                    }
                }
                """)).code());
    }

    @Test
    public void typeArgumentCountMismatchInImplementsIsRejected() {
        assertEquals(DiagnosticCode.TYPE_TYPE_ARGUMENT_ARITY, first(checkFails("""
                interface Container<T> {
                    fun get(): T
                }
                class StringBox implements Container<String, Int> {
                    fun get(): String {
                        return "x"
                    }
                }
                """)).code());
    }

    @Test
    public void wrongArgumentTypeForGenericFunctionIsRejected() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails("""
                fun identity<T>(x: T): T {
                    return x
                }
                fun f(): Unit {
                    val y: Int = identity("x")
                }
                """)).code());
    }
}
