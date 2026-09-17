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
        String src = "func f(): Int {\n    return y\n}\n";
        Diagnostic diagnostic = first(checkFails(src));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_NAME, diagnostic.code());
        int y = src.indexOf('y');
        assertEquals(SourceSpan.of(y, y + 1), diagnostic.span());
    }

    @Test
    public void useBeforeDeclarationIsAnUnknownName() {
        Diagnostic diagnostic = first(checkFails("func f(): Int {\n    val y = x\n    val x = 1\n    return y\n}\n"));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_NAME, diagnostic.code());
    }

    @Test
    public void unknownTypeIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Widget {\n    return 1\n}\n"));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_TYPE, diagnostic.code());
    }

    @Test
    public void duplicateFunctionNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n}\nfunc f(): Unit {\n}\n"));
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
    }

    @Test
    public void duplicateParameterNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(a: Int, a: Int): Unit {\n}\n"));
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
    }

    @Test
    public void duplicateLocalNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    val x = 1\n    val x = 2\n}\n"));
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
    }

    @Test
    public void initializerMustBeAssignableToTheDeclaredType() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    val x: Int = \"s\"\n}\n"));
        assertEquals(DiagnosticCode.TYPE_MISMATCH, diagnostic.code());
        assertEquals("Int", diagnostic.expected().orElseThrow());
        assertEquals("String", diagnostic.found().orElseThrow());
    }

    @Test
    public void assignmentToValIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    val x = 1\n    x = 2\n}\n"));
        assertEquals(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, diagnostic.code());
    }

    @Test
    public void assignmentToParameterIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(x: Int): Unit {\n    x = 1\n}\n"));
        assertEquals(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE, diagnostic.code());
    }

    @Test
    public void nonReferenceAssignmentTargetIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    1 = 2\n}\n"));
        assertEquals(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET, diagnostic.code());
    }

    @Test
    public void assignmentValueTypeIsChecked() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    var x = 1\n    x = \"s\"\n}\n"));
        assertEquals(DiagnosticCode.TYPE_MISMATCH, diagnostic.code());
    }

    @Test
    public void invalidArithmeticOperandsAreRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Int {\n    return true + 1\n}\n"));
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, diagnostic.code());
    }

    @Test
    public void mixedStringAndIntConcatenationIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): String {\n    return \"a\" + 1\n}\n"));
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, diagnostic.code());
    }

    @Test
    public void comparisonRequiresInts() {
        Diagnostic diagnostic = first(checkFails("func f(): Boolean {\n    return 1 < true\n}\n"));
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, diagnostic.code());
    }

    @Test
    public void equalityRequiresCompatibleOperands() {
        Diagnostic diagnostic = first(checkFails("func f(): Boolean {\n    return \"a\" == 1\n}\n"));
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, diagnostic.code());
    }

    @Test
    public void logicalOperatorsRequireBooleans() {
        Diagnostic diagnostic = first(checkFails("func f(): Boolean {\n    return true && 1\n}\n"));
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, diagnostic.code());
    }

    @Test
    public void unaryOperatorsRequireTheirOperandType() {
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, first(checkFails("func f(): Boolean {\n    return !1\n}\n")).code());
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, first(checkFails("func f(): Int {\n    return -true\n}\n")).code());
    }

    @Test
    public void ifConditionMustBeBoolean() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    if (1) {\n        return\n    }\n}\n"));
        assertEquals(DiagnosticCode.TYPE_CONDITION_NOT_BOOLEAN, diagnostic.code());
    }

    @Test
    public void whileConditionMustBeBoolean() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    while (1) {\n        return\n    }\n}\n"));
        assertEquals(DiagnosticCode.TYPE_CONDITION_NOT_BOOLEAN, diagnostic.code());
    }

    @Test
    public void forConditionMustBeBoolean() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    for (; 1; ) {\n        return\n    }\n}\n"));
        assertEquals(DiagnosticCode.TYPE_CONDITION_NOT_BOOLEAN, diagnostic.code());
    }

    @Test
    public void returnValueTypeMustMatchTheDeclaredReturnType() {
        Diagnostic diagnostic = first(checkFails("func f(): Int {\n    return \"s\"\n}\n"));
        assertEquals(DiagnosticCode.TYPE_RETURN_MISMATCH, diagnostic.code());
    }

    @Test
    public void bareReturnIsRejectedInAValueReturningFunction() {
        Diagnostic diagnostic = first(checkFails("func f(): Int {\n    return\n}\n"));
        assertEquals(DiagnosticCode.TYPE_MISSING_RETURN_VALUE, diagnostic.code());
    }

    @Test
    public void valueReturnIsRejectedInAUnitFunction() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    return 1\n}\n"));
        assertEquals(DiagnosticCode.TYPE_UNEXPECTED_RETURN_VALUE, diagnostic.code());
    }

    @Test
    public void missingReturnPathIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Int {\n    if (true) {\n        return 1\n    }\n}\n"));
        assertEquals(DiagnosticCode.TYPE_MISSING_RETURN_PATH, diagnostic.code());
    }

    @Test
    public void explicitMainIsRejected() {
        Diagnostic diagnostic = first(checkFails("func main(): Unit {\n}\n"));
        assertEquals(DiagnosticCode.SEM_INVALID_ENTRY_POINT, diagnostic.code());
    }

    @Test
    public void explicitMainIsRejectedEvenWithTopLevelStatements() {
        Diagnostic diagnostic = first(checkFails("func main(): Unit {\n}\nprintln(\"x\")\n"));
        assertEquals(DiagnosticCode.SEM_INVALID_ENTRY_POINT, diagnostic.code());
    }

    @Test
    public void aTopLevelReturnValueIsRejected() {
        Diagnostic diagnostic = first(checkFails("return 1\n"));
        assertEquals(DiagnosticCode.TYPE_UNEXPECTED_RETURN_VALUE, diagnostic.code());
    }

    @Test
    public void topLevelBreakOutsideALoopIsRejected() {
        Diagnostic diagnostic = first(checkFails("break\n"));
        assertEquals(DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP, diagnostic.code());
    }

    @Test
    public void callArityMustBeExact() {
        Diagnostic diagnostic = first(checkFails("func g(a: Int): Unit {\n}\nfunc f(): Unit {\n    g(1, 2)\n}\n"));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
    }

    @Test
    public void callArgumentTypesAreChecked() {
        Diagnostic diagnostic = first(checkFails("func g(a: Int): Unit {\n}\nfunc f(): Unit {\n    g(\"s\")\n}\n"));
        assertEquals(DiagnosticCode.TYPE_MISMATCH, diagnostic.code());
    }

    @Test
    public void exitRequiresAnIntArgument() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    exit(\"x\")\n}\n"));
        assertEquals(DiagnosticCode.TYPE_MISMATCH, diagnostic.code());
    }

    @Test
    public void exitArityMustBeExact() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    exit()\n}\n"));
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
    }

    @Test
    public void unknownCalleeIsReported() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    nope()\n}\n"));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_NAME, diagnostic.code());
    }

    @Test
    public void callingANonFunctionIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    val x = 1\n    x()\n}\n"));
        assertEquals(DiagnosticCode.TYPE_NOT_CALLABLE, diagnostic.code());
    }

    @Test
    public void functionNamesAreNotValues() {
        Diagnostic diagnostic = first(checkFails("func g(): Int {\n    return 1\n}\nfunc f(): Int {\n    val x = g\n    return x\n}\n"));
        assertEquals(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, diagnostic.code());
    }

    @Test
    public void valueProducingExpressionCannotBeAStatement() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    1 + 2\n}\n"));
        assertEquals(DiagnosticCode.SEM_VALUE_EXPRESSION_STATEMENT, diagnostic.code());
    }

    @Test
    public void intLiteralOutsideSignedRangeIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Int {\n    return 2147483648\n}\n"));
        assertEquals(DiagnosticCode.TYPE_INT_LITERAL_OUT_OF_RANGE, diagnostic.code());
    }

    @Test
    public void breakOutsideLoopIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    break\n}\n"));
        assertEquals(DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP, diagnostic.code());
    }

    @Test
    public void continueOutsideLoopIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    continue\n}\n"));
        assertEquals(DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP, diagnostic.code());
    }

    @Test
    public void forInitializerMustBeADeclarationOrAssignment() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    for (1; ; ) {\n        return\n    }\n}\n"));
        assertEquals(DiagnosticCode.SEM_FOR_INITIALIZER, diagnostic.code());
    }

    @Test
    public void forUpdateMustBeAnAssignment() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    for (; ; 1) {\n        return\n    }\n}\n"));
        assertEquals(DiagnosticCode.SEM_FOR_UPDATE, diagnostic.code());
    }

    @Test
    public void memberAccessOnNonClassIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(s: String): Unit {\n    val x = s.length\n}\n"));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_MEMBER, diagnostic.code());
    }

    @Test
    public void classNamesAreNotValues() {
        Diagnostic diagnostic = first(checkFails("class User {\n    val name: String\n    User(name: String) {\n        this.name = name\n    }\n}\nfunc f(): User {\n    val x = User\n    return x\n}\n"));
        assertEquals(DiagnosticCode.TYPE_CLASS_AS_VALUE, diagnostic.code());
    }

    @Test
    public void anyIsNotImplicitlyConvertedToASpecificType() {
        Diagnostic diagnostic = first(checkFails("func f(x: Any): String {\n    return x\n}\n"));
        assertEquals(DiagnosticCode.TYPE_RETURN_MISMATCH, diagnostic.code());
    }
}
