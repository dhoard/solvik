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
 * Negative Phase 6 static-semantics tests: undeclared members, property mutability, constructor
 * definite initialization, method/constructor argument checking, and class-specific name
 * resolution each produce a source-located diagnostic and no typed result.
 */
public final class SolvikClassSemanticNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("classneg.sol", text);
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
    public void undeclaredPropertyReadIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Int = 1
                }
                func f(): Int {
                    return C().y
                }
                """));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_MEMBER, diagnostic.code());
    }

    @Test
    public void undeclaredPropertyWriteIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Int = 1
                }
                func f(): Unit {
                    C().y = 2
                }
                """));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_MEMBER, diagnostic.code());
    }

    @Test
    public void memberAccessOnABuiltinIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(s: String): Unit {\n    val x = s.length\n}\n"));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_MEMBER, diagnostic.code());
    }

    @Test
    public void writeToValPropertyAfterConstructionIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Int
                    init() {
                        this.x = 1
                    }
                    func reset(): Unit {
                        this.x = 2
                    }
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, diagnostic.code());
    }

    @Test
    public void writeToValPropertyWithInitializerInInitIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Int = 1
                    init() {
                        this.x = 2
                    }
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, diagnostic.code());
    }

    @Test
    public void doubleAssignmentToValPropertyInInitIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Int
                    init() {
                        this.x = 1
                        this.x = 2
                    }
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, diagnostic.code());
    }

    @Test
    public void readOfPropertyBeforeInitializationIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Int
                    init() {
                        val before = this.x
                        this.x = 1
                    }
                }
                """));
        assertEquals(DiagnosticCode.TYPE_UNINITIALIZED_PROPERTY, diagnostic.code());
    }

    @Test
    public void propertyMissingOnOneConstructorPathIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Int
                    init() {
                        if (true) {
                            this.x = 1
                        }
                    }
                }
                """));
        assertEquals(DiagnosticCode.TYPE_MISSING_PROPERTY_INITIALIZER, diagnostic.code());
    }

    @Test
    public void earlyReturnWithoutInitializationIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Int
                    init() {
                        if (true) {
                            return
                        }
                        this.x = 1
                    }
                }
                """));
        assertEquals(DiagnosticCode.TYPE_MISSING_PROPERTY_INITIALIZER, diagnostic.code());
    }

    @Test
    public void classWithoutInitNeedsEveryPropertyInitialized() {
        Diagnostic diagnostic = first(checkFails("class C {\n    val x: Int\n}\n"));
        assertEquals(DiagnosticCode.SEM_CLASS_REQUIRES_INITIALIZER, diagnostic.code());
    }

    @Test
    public void wrongConstructorArgumentTypeIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Int
                    init(x: Int) {
                        this.x = x
                    }
                }
                func f(): C {
                    return C("s")
                }
                """));
        assertEquals(DiagnosticCode.TYPE_MISMATCH, diagnostic.code());
    }

    @Test
    public void constructorArityMustMatch() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Int
                    init(x: Int) {
                        this.x = x
                    }
                }
                func f(): C {
                    return C()
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
    }

    @Test
    public void wrongMethodArgumentTypeIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    func set(x: Int): Unit {
                    }
                }
                func f(): Unit {
                    C().set("s")
                }
                """));
        assertEquals(DiagnosticCode.TYPE_MISMATCH, diagnostic.code());
    }

    @Test
    public void unknownMethodIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Int = 1
                }
                func f(): Unit {
                    C().missing()
                }
                """));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_MEMBER, diagnostic.code());
    }

    @Test
    public void callingAPropertyIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Int = 1
                }
                func f(): Unit {
                    C().x()
                }
                """));
        assertEquals(DiagnosticCode.TYPE_NOT_CALLABLE, diagnostic.code());
    }

    @Test
    public void methodUsedAsAValueIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    func f(): Int {
                        return 1
                    }
                }
                func g(): Int {
                    val h = C().f
                    return 1
                }
                """));
        assertEquals(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, diagnostic.code());
    }

    @Test
    public void assigningToAMethodIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    func f(): Unit {
                    }
                }
                func g(): Unit {
                    C().f = 1
                }
                """));
        assertEquals(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, diagnostic.code());
    }

    @Test
    public void thisOutsideAClassIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    val x = this\n}\n"));
        assertEquals(DiagnosticCode.RESOL_THIS_OUTSIDE_CLASS, diagnostic.code());
    }

    @Test
    public void classNamesAreNotValues() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Int = 1
                }
                func f(): C {
                    val c = C
                    return c
                }
                """));
        assertEquals(DiagnosticCode.TYPE_CLASS_AS_VALUE, diagnostic.code());
    }

    @Test
    public void duplicatePropertyIsRejected() {
        Diagnostic diagnostic = first(checkFails("class C {\n    val x: Int = 1\n    val x: Int = 2\n}\n"));
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
    }

    @Test
    public void duplicateMethodIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    func f(): Unit {
                    }
                    func f(): Unit {
                    }
                }
                """));
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
    }

    @Test
    public void propertyAndMethodMayNotShareAName() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Int = 1
                    func x(): Int {
                        return 1
                    }
                }
                """));
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
    }

    @Test
    public void moreThanOneInitIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    init() {
                    }
                    init(x: Int) {
                    }
                }
                """));
        assertEquals(DiagnosticCode.SEM_DUPLICATE_INIT, diagnostic.code());
    }

    @Test
    public void duplicateClassNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("class C {\n}\nclass C {\n}\n"));
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
    }

    @Test
    public void classMayNotShadowABuiltinTypeName() {
        Diagnostic diagnostic = first(checkFails("class String {\n}\n"));
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
    }

    @Test
    public void propertyInitializerTypeMustMatch() {
        Diagnostic diagnostic = first(checkFails("class C {\n    val x: Int = \"s\"\n}\n"));
        assertEquals(DiagnosticCode.TYPE_MISMATCH, diagnostic.code());
    }

    @Test
    public void initMayNotReturnAValue() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Int = 1
                    init() {
                        return 1
                    }
                }
                """));
        assertEquals(DiagnosticCode.TYPE_UNEXPECTED_RETURN_VALUE, diagnostic.code());
    }

    @Test
    public void valueReturningMethodNeedsReturnOnEveryPath() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    func f(): Int {
                        if (true) {
                            return 1
                        }
                    }
                }
                """));
        assertEquals(DiagnosticCode.TYPE_MISSING_RETURN_PATH, diagnostic.code());
    }
}
