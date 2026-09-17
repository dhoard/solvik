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
 * Negative Phase 7 semantic tests: extending final classes, accidental or invalid overrides,
 * inheritance cycles, invalid superclasses, and misplaced or missing {@code super} uses each
 * produce a source-located diagnostic and no typed result.
 */
public final class SolvikInheritanceNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("inheritneg.sol", text);
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
    public void extendingAFinalClassIsRejected() {
        Diagnostic diagnostic = first(checkFails("class A {\n}\nclass B extends A {\n}\n"));
        assertEquals(DiagnosticCode.SEM_EXTEND_FINAL, diagnostic.code());
    }

    @Test
    public void accidentalOverrideWithoutTheKeywordIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    open func f(): Int {
                        return 1
                    }
                }
                class B extends A {
                    func f(): Int {
                        return 2
                    }
                }
                """));
        assertEquals(DiagnosticCode.SEM_ACCIDENTAL_OVERRIDE, diagnostic.code());
    }

    @Test
    public void overridingAFinalMethodIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    func f(): Int {
                        return 1
                    }
                }
                class B extends A {
                    override func f(): Int {
                        return 2
                    }
                }
                """));
        assertEquals(DiagnosticCode.SEM_OVERRIDE_FINAL, diagnostic.code());
    }

    @Test
    public void overrideWithoutAnInheritedMethodIsRejected() {
        Diagnostic diagnostic = first(checkFails("class B {\n    override func f(): Int {\n        return 2\n    }\n}\n"));
        assertEquals(DiagnosticCode.SEM_OVERRIDE_WITHOUT_SUPER, diagnostic.code());
    }

    @Test
    public void covariantReturnTypesAreAcceptedButUnrelatedOnesAreRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    open func f(): Int {
                        return 1
                    }
                }
                class B extends A {
                    override func f(): String {
                        return "x"
                    }
                }
                """));
        assertEquals(DiagnosticCode.SEM_OVERRIDE_SIGNATURE, diagnostic.code());
    }

    @Test
    public void mismatchedOverrideParameterTypesAreRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    open func f(x: Int): Int {
                        return x
                    }
                }
                class B extends A {
                    override func f(x: String): Int {
                        return 1
                    }
                }
                """));
        assertEquals(DiagnosticCode.SEM_OVERRIDE_SIGNATURE, diagnostic.code());
    }

    @Test
    public void inheritanceCyclesAreRejected() {
        Diagnostic diagnostic = first(checkFails("class A extends B {\n}\nclass B extends A {\n}\n"));
        assertEquals(DiagnosticCode.SEM_INHERITANCE_CYCLE, diagnostic.code());
    }

    @Test
    public void extendingANonClassBuiltinIsRejected() {
        Diagnostic diagnostic = first(checkFails("class A extends Int {\n}\n"));
        assertEquals(DiagnosticCode.SEM_INVALID_SUPERCLASS, diagnostic.code());
    }

    @Test
    public void superOutsideAClassIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Int {\n    return super.g()\n}\n"));
        assertEquals(DiagnosticCode.RESOL_SUPER_OUTSIDE_CLASS, diagnostic.code());
    }

    @Test
    public void superWithoutASuperclassIsRejected() {
        Diagnostic diagnostic = first(checkFails("class A {\n    func f(): Int {\n        return super.g()\n    }\n}\n"));
        assertEquals(DiagnosticCode.RESOL_SUPER_OUTSIDE_CLASS, diagnostic.code());
    }

    @Test
    public void bareSuperAsAValueIsRejected() {
        Diagnostic diagnostic = first(checkFails("open class A {\n}\nclass B extends A {\n    func f(): A {\n        val x = super;\n        return x;\n    }\n}\n"));
        assertEquals(DiagnosticCode.SEM_SUPER_AS_VALUE, diagnostic.code());
    }

    @Test
    public void superCallMustBeTheFirstInitStatement() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    init(x: Int) {
                    }
                }
                class B extends A {
                    init() {
                        val y = 1
                        super(1)
                    }
                }
                """));
        assertEquals(DiagnosticCode.SEM_SUPER_CALL_PLACEMENT, diagnostic.code());
    }

    @Test
    public void missingExplicitSuperCallIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    init(x: Int) {
                    }
                }
                class B extends A {
                    init() {
                    }
                }
                """));
        assertEquals(DiagnosticCode.SEM_MISSING_SUPER_INIT, diagnostic.code());
    }

    @Test
    public void subclassWithoutInitCannotSatisfyASuperclassRequiringArguments() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    init(x: Int) {
                    }
                }
                class B extends A {
                    val y: Int = 1
                }
                """));
        assertEquals(DiagnosticCode.SEM_MISSING_SUPER_INIT_IMPLICIT, diagnostic.code());
    }

    @Test
    public void redeclaringAnInheritedPropertyIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    val x: Int = 1
                }
                class B extends A {
                    val x: Int = 2
                }
                """));
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
    }

    @Test
    public void writingAnInheritedValPropertyIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    val x: Int = 1
                }
                class B extends A {
                    func reset(): Unit {
                        this.x = 2
                    }
                }
                """));
        assertEquals(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, diagnostic.code());
    }
}
