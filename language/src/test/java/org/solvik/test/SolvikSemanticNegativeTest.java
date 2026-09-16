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
import org.solvik.source.SourceSpan;

/**
 * Negative Phase 4 static-semantics tests: each incorrect program parses but produces a
 * source-located diagnostic and no typed result. The first diagnostic is asserted so the tests pin
 * the classification, not merely the failure.
 */
public final class SolvikSemanticNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("neg.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertFalse("analysis must fail: " + text, result.isSuccess());
        assertTrue("failed analysis must expose no program", result.program().isEmpty());
        assertTrue("failed analysis must carry diagnostics", result.diagnostics().hasErrors());
        try {
            result.requireProgram();
            org.junit.Assert.fail("requireProgram() must reject failed analysis");
        } catch (IllegalStateException expected) {
            // expected
        }
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
    public void unknownNameIsReportedAtTheReference() {
        String src = "fun f(): Int {\n    return y\n}\n";
        Diagnostic diagnostic = first(checkFails(src));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_NAME, diagnostic.code());
        int y = src.indexOf('y');
        assertEquals(SourceSpan.of(y, y + 1), diagnostic.span());
    }

    @Test
    public void useBeforeDeclarationIsAnUnknownName() {
        Diagnostic diagnostic = first(checkFails("fun f(): Int {\n    val y = x\n    val x = 1\n    return y\n}\n"));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_NAME, diagnostic.code());
    }

    @Test
    public void unknownTypeIsRejected() {
        Diagnostic diagnostic = first(checkFails("fun f(): Widget {\n    return 1\n}\n"));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_TYPE, diagnostic.code());
    }

    @Test
    public void duplicateFunctionNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n}\nfun f(): Unit {\n}\n"));
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
    }

    @Test
    public void duplicateParameterNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("fun f(a: Int, a: Int): Unit {\n}\n"));
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
    }

    @Test
    public void duplicateLocalNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    val x = 1\n    val x = 2\n}\n"));
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
    }

    @Test
    public void initializerMustBeAssignableToTheDeclaredType() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    val x: Int = \"s\"\n}\n"));
        assertEquals(DiagnosticCode.TYPE_MISMATCH, diagnostic.code());
        assertEquals("Int", diagnostic.expected().orElseThrow());
        assertEquals("String", diagnostic.found().orElseThrow());
    }

    @Test
    public void assignmentToValIsRejected() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    val x = 1\n    x = 2\n}\n"));
        assertEquals(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, diagnostic.code());
    }

    @Test
    public void assignmentToParameterIsRejected() {
        Diagnostic diagnostic = first(checkFails("fun f(x: Int): Unit {\n    x = 1\n}\n"));
        assertEquals(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, diagnostic.code());
    }

    @Test
    public void nonReferenceAssignmentTargetIsRejected() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    1 = 2\n}\n"));
        assertEquals(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, diagnostic.code());
    }

    @Test
    public void assignmentValueTypeIsChecked() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    var x = 1\n    x = \"s\"\n}\n"));
        assertEquals(DiagnosticCode.TYPE_MISMATCH, diagnostic.code());
    }

    @Test
    public void invalidArithmeticOperandsAreRejected() {
        Diagnostic diagnostic = first(checkFails("fun f(): Int {\n    return true + 1\n}\n"));
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, diagnostic.code());
    }

    @Test
    public void mixedStringAndIntConcatenationIsRejected() {
        Diagnostic diagnostic = first(checkFails("fun f(): String {\n    return \"a\" + 1\n}\n"));
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, diagnostic.code());
    }

    @Test
    public void comparisonRequiresInts() {
        Diagnostic diagnostic = first(checkFails("fun f(): Boolean {\n    return 1 < true\n}\n"));
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, diagnostic.code());
    }

    @Test
    public void equalityRequiresCompatibleOperands() {
        Diagnostic diagnostic = first(checkFails("fun f(): Boolean {\n    return \"a\" == 1\n}\n"));
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, diagnostic.code());
    }

    @Test
    public void logicalOperatorsRequireBooleans() {
        Diagnostic diagnostic = first(checkFails("fun f(): Boolean {\n    return true && 1\n}\n"));
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, diagnostic.code());
    }

    @Test
    public void unaryOperatorsRequireTheirOperandType() {
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, first(checkFails("fun f(): Boolean {\n    return !1\n}\n")).code());
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, first(checkFails("fun f(): Int {\n    return -true\n}\n")).code());
    }

    @Test
    public void ifConditionMustBeBoolean() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    if (1) {\n        return\n    }\n}\n"));
        assertEquals(DiagnosticCode.TYPE_CONDITION_NOT_BOOLEAN, diagnostic.code());
    }

    @Test
    public void whileConditionMustBeBoolean() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    while (1) {\n        return\n    }\n}\n"));
        assertEquals(DiagnosticCode.TYPE_CONDITION_NOT_BOOLEAN, diagnostic.code());
    }

    @Test
    public void forConditionMustBeBoolean() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    for (; 1; ) {\n        return\n    }\n}\n"));
        assertEquals(DiagnosticCode.TYPE_CONDITION_NOT_BOOLEAN, diagnostic.code());
    }

    @Test
    public void returnValueTypeMustMatchTheDeclaredReturnType() {
        Diagnostic diagnostic = first(checkFails("fun f(): Int {\n    return \"s\"\n}\n"));
        assertEquals(DiagnosticCode.TYPE_RETURN_MISMATCH, diagnostic.code());
    }

    @Test
    public void bareReturnIsRejectedInAValueReturningFunction() {
        Diagnostic diagnostic = first(checkFails("fun f(): Int {\n    return\n}\n"));
        assertEquals(DiagnosticCode.TYPE_MISSING_RETURN_VALUE, diagnostic.code());
    }

    @Test
    public void valueReturnIsRejectedInAUnitFunction() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    return 1\n}\n"));
        assertEquals(DiagnosticCode.TYPE_UNEXPECTED_RETURN_VALUE, diagnostic.code());
    }

    @Test
    public void missingReturnPathIsRejected() {
        Diagnostic diagnostic = first(checkFails("fun f(): Int {\n    if (true) {\n        return 1\n    }\n}\n"));
        assertEquals(DiagnosticCode.TYPE_MISSING_RETURN_PATH, diagnostic.code());
    }

    @Test
    public void entryPointMustTakeNoParameters() {
        Diagnostic diagnostic = first(checkFails("fun main(x: Int): Unit {\n}\n"));
        assertEquals(DiagnosticCode.SEM_INVALID_ENTRY_POINT, diagnostic.code());
    }

    @Test
    public void entryPointMustReturnUnit() {
        Diagnostic diagnostic = first(checkFails("fun main(): Int {\n    return 1\n}\n"));
        assertEquals(DiagnosticCode.SEM_INVALID_ENTRY_POINT, diagnostic.code());
    }

    @Test
    public void callArityMustBeExact() {
        Diagnostic diagnostic = first(checkFails("fun g(a: Int): Unit {\n}\nfun f(): Unit {\n    g(1, 2)\n}\n"));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
    }

    @Test
    public void callArgumentTypesAreChecked() {
        Diagnostic diagnostic = first(checkFails("fun g(a: Int): Unit {\n}\nfun f(): Unit {\n    g(\"s\")\n}\n"));
        assertEquals(DiagnosticCode.TYPE_MISMATCH, diagnostic.code());
    }

    @Test
    public void unknownCalleeIsReported() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    nope()\n}\n"));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_NAME, diagnostic.code());
    }

    @Test
    public void callingANonFunctionIsRejected() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    val x = 1\n    x()\n}\n"));
        assertEquals(DiagnosticCode.TYPE_NOT_CALLABLE, diagnostic.code());
    }

    @Test
    public void functionNamesAreNotValues() {
        Diagnostic diagnostic = first(checkFails("fun g(): Int {\n    return 1\n}\nfun f(): Int {\n    val x = g\n    return x\n}\n"));
        assertEquals(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, diagnostic.code());
    }

    @Test
    public void valueProducingExpressionCannotBeAStatement() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    1 + 2\n}\n"));
        assertEquals(DiagnosticCode.SEM_VALUE_EXPRESSION_STATEMENT, diagnostic.code());
    }

    @Test
    public void intLiteralOutsideSignedRangeIsRejected() {
        Diagnostic diagnostic = first(checkFails("fun f(): Int {\n    return 2147483648\n}\n"));
        assertEquals(DiagnosticCode.TYPE_INT_LITERAL_OUT_OF_RANGE, diagnostic.code());
    }

    @Test
    public void breakOutsideLoopIsRejected() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    break\n}\n"));
        assertEquals(DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP, diagnostic.code());
    }

    @Test
    public void continueOutsideLoopIsRejected() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    continue\n}\n"));
        assertEquals(DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP, diagnostic.code());
    }

    @Test
    public void forInitializerMustBeADeclarationOrAssignment() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    for (1; ; ) {\n        return\n    }\n}\n"));
        assertEquals(DiagnosticCode.SEM_FOR_INITIALIZER, diagnostic.code());
    }

    @Test
    public void forUpdateMustBeAnAssignment() {
        Diagnostic diagnostic = first(checkFails("fun f(): Unit {\n    for (; ; 1) {\n        return\n    }\n}\n"));
        assertEquals(DiagnosticCode.SEM_FOR_UPDATE, diagnostic.code());
    }

    @Test
    public void memberAccessOnNonClassIsRejected() {
        Diagnostic diagnostic = first(checkFails("fun f(s: String): Unit {\n    val x = s.length\n}\n"));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_MEMBER, diagnostic.code());
    }

    @Test
    public void classNamesAreNotValues() {
        Diagnostic diagnostic = first(checkFails("class User {\n    val name: String\n    init(name: String) {\n        this.name = name\n    }\n}\nfun f(): User {\n    val x = User\n    return x\n}\n"));
        assertEquals(DiagnosticCode.TYPE_CLASS_AS_VALUE, diagnostic.code());
    }

    @Test
    public void anyIsNotImplicitlyConvertedToASpecificType() {
        Diagnostic diagnostic = first(checkFails("fun f(x: Any): String {\n    return x\n}\n"));
        assertEquals(DiagnosticCode.TYPE_RETURN_MISMATCH, diagnostic.code());
    }
}
