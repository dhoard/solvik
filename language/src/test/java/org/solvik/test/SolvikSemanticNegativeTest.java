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
import static org.assertj.core.api.Assertions.fail;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.jupiter.api.Test;
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
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        assertThat(result.program().isEmpty()).as("failed analysis must expose no program").isTrue();
        assertThat(result.diagnostics().hasErrors()).as("failed analysis must carry diagnostics").isTrue();
        try {
            result.requireProgram();
            fail("requireProgram() must reject failed analysis");
        } catch (IllegalStateException expected) {
            // expected
        }
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
    public void unknownNameIsReportedAtTheReference() {
        String src = "func f(): Integer {\n    return y\n}\n";
        Diagnostic diagnostic = first(checkFails(src));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
        int y = src.indexOf('y');
        assertThat(diagnostic.span()).isEqualTo(SourceSpan.of(y, y + 1));
    }

    @Test
    public void useBeforeDeclarationIsAnUnknownName() {
        Diagnostic diagnostic = first(checkFails("func f(): Integer {\n    val y = x\n    val x = 1\n    return y\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void unknownTypeIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Widget {\n    return 1\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }

    @Test
    public void retiredIntTypeNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Int {\n    return 1\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }

    @Test
    public void retiredCharTypeNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Char {\n    return 1\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }

    @Test
    public void retiredIntIsNotAvailableAsATypeTestTarget() {
        Diagnostic diagnostic = first(checkFails("func f(v: Any): Boolean {\n    return v is Int\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }

    @Test
    public void retiredCharIsNotAvailableAsACollectionTypeArgument() {
        Diagnostic diagnostic = first(checkFails("val chars: Set<Char> = Set('a')\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }

    @Test
    public void objectWithoutAUserDeclarationIsAnUnknownTypeAtItsReference() {
        String src = "func f(): Object {\n    return 1\n}\n";
        Diagnostic diagnostic = first(checkFails(src));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
        int start = src.indexOf("Object");
        assertThat(diagnostic.span()).isEqualTo(SourceSpan.of(start, start + "Object".length()));
    }

    @Test
    public void objectIsNotAvailableAsATypeTestTarget() {
        Diagnostic diagnostic = first(checkFails("func f(v: Any): Boolean {\n    return v is Object\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }

    @Test
    public void objectIsNotAvailableAsACastTarget() {
        Diagnostic diagnostic = first(checkFails("func f(v: Any): Any {\n    return v as Object\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }

    @Test
    public void duplicateFunctionNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n}\nfunc f(): Unit {\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void duplicateParameterNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(a: Integer, a: Integer): Unit {\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void duplicateLocalNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    val x = 1\n    val x = 2\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void initializerMustBeAssignableToTheDeclaredType() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    val x: Integer = \"s\"\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
        assertThat(diagnostic.expected().orElseThrow()).isEqualTo("Integer");
        assertThat(diagnostic.found().orElseThrow()).isEqualTo("String");
    }

    @Test
    public void assignmentToValIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    val x = 1\n    x = 2\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }

    @Test
    public void assignmentToParameterIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(x: Integer): Unit {\n    x = 1\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }

    @Test
    public void nonReferenceAssignmentTargetIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    1 = 2\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET);
    }

    @Test
    public void assignmentValueTypeIsChecked() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    var x = 1\n    x = \"s\"\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void invalidArithmeticOperandsAreRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Integer {\n    return true + 1\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void mixedStringAndIntegerConcatenationIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): String {\n    return \"a\" + 1\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void comparisonRequiresIntegers() {
        Diagnostic diagnostic = first(checkFails("func f(): Boolean {\n    return 1 < true\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void equalityRequiresCompatibleOperands() {
        Diagnostic diagnostic = first(checkFails("func f(): Boolean {\n    return \"a\" == 1\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void logicalOperatorsRequireBooleans() {
        Diagnostic diagnostic = first(checkFails("func f(): Boolean {\n    return true && 1\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void unaryOperatorsRequireTheirOperandType() {
        assertThat(first(checkFails("func f(): Boolean {\n    return !1\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
        assertThat(first(checkFails("func f(): Integer {\n    return -true\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void ifConditionMustBeBoolean() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    if (1) {\n        return\n    }\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_CONDITION_NOT_BOOLEAN);
    }

    @Test
    public void whileConditionMustBeBoolean() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    while (1) {\n        return\n    }\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_CONDITION_NOT_BOOLEAN);
    }

    @Test
    public void forConditionMustBeBoolean() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    for (; 1; ) {\n        return\n    }\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_CONDITION_NOT_BOOLEAN);
    }

    @Test
    public void returnValueTypeMustMatchTheDeclaredReturnType() {
        Diagnostic diagnostic = first(checkFails("func f(): Integer {\n    return \"s\"\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_RETURN_MISMATCH);
    }

    @Test
    public void bareReturnIsRejectedInAValueReturningFunction() {
        Diagnostic diagnostic = first(checkFails("func f(): Integer {\n    return\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISSING_RETURN_VALUE);
    }

    @Test
    public void valueReturnIsRejectedInAUnitFunction() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    return 1\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_UNEXPECTED_RETURN_VALUE);
    }

    @Test
    public void valueReturnIsRejectedWhenTheReturnTypeIsOmitted() {
        // An omitted return type is Unit, so returning a value is the same error as `: Unit`.
        Diagnostic diagnostic = first(checkFails("func f() {\n    return 1\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_UNEXPECTED_RETURN_VALUE);
    }

    @Test
    public void missingReturnPathIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Integer {\n    if (true) {\n        return 1\n    }\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISSING_RETURN_PATH);
    }

    @Test
    public void explicitMainIsRejected() {
        Diagnostic diagnostic = first(checkFails("func main(): Unit {\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_INVALID_ENTRY_POINT);
    }

    @Test
    public void explicitMainIsRejectedEvenWithTopLevelStatements() {
        Diagnostic diagnostic = first(checkFails("func main(): Unit {\n}\nprintln(\"x\")\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_INVALID_ENTRY_POINT);
    }

    @Test
    public void aTopLevelReturnValueIsRejected() {
        Diagnostic diagnostic = first(checkFails("return 1\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_UNEXPECTED_RETURN_VALUE);
    }

    @Test
    public void topLevelBreakOutsideALoopIsRejected() {
        Diagnostic diagnostic = first(checkFails("break\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP);
    }

    @Test
    public void callArityMustBeExact() {
        Diagnostic diagnostic = first(checkFails("func g(a: Integer): Unit {\n}\nfunc f(): Unit {\n    g(1, 2)\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void callArgumentTypesAreChecked() {
        Diagnostic diagnostic = first(checkFails("func g(a: Integer): Unit {\n}\nfunc f(): Unit {\n    g(\"s\")\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void exitRequiresAnIntegerArgument() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    exit(\"x\")\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void exitArityMustBeExact() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    exit()\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void unknownCalleeIsReported() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    nope()\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void callingANonFunctionIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    val x = 1\n    x()\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_NOT_CALLABLE);
    }

    @Test
    public void functionNamesAreNotValues() {
        Diagnostic diagnostic = first(checkFails("func g(): Integer {\n    return 1\n}\nfunc f(): Integer {\n    val x = g\n    return x\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_FUNCTION_AS_VALUE);
    }

    @Test
    public void valueProducingExpressionCannotBeAStatement() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    1 + 2\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_VALUE_EXPRESSION_STATEMENT);
    }

    @Test
    public void intLiteralOutsideSignedRangeIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Integer {\n    return 2147483648\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_INTEGER_LITERAL_OUT_OF_RANGE);
    }

    @Test
    public void breakOutsideLoopIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    break\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP);
    }

    @Test
    public void continueOutsideLoopIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    continue\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP);
    }

    @Test
    public void forInitializerMustBeADeclarationOrAssignment() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    for (1; ; ) {\n        return\n    }\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_FOR_INITIALIZER);
    }

    @Test
    public void forUpdateMustBeAnAssignment() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    for (; ; 1) {\n        return\n    }\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_FOR_UPDATE);
    }

    @Test
    public void memberAccessOnNonClassIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(s: String): Unit {\n    val x = s.length\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void classNamesAreNotValues() {
        Diagnostic diagnostic = first(checkFails("class User {\n    val name: String\n    User(name: String) {\n        this.name = name\n    }\n}\nfunc f(): User {\n    val x = User\n    return x\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_CLASS_AS_VALUE);
    }

    @Test
    public void anyIsNotImplicitlyConvertedToASpecificType() {
        Diagnostic diagnostic = first(checkFails("func f(x: Any): String {\n    return x\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_RETURN_MISMATCH);
    }

    @Test
    public void redeclarationInTheSameScopeBlockIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    {\n        val x = 1\n        val x = 2\n    }\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void duplicateObjectDeclarationsAreRejected() {
        Diagnostic diagnostic = first(checkFails("class Object {\n}\nclass Object {\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void objectConversionIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    val x = Object(1)\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void objectAsAGenericTypeArgumentIsUnknown() {
        Diagnostic diagnostic = first(checkFails("func f(values: List<Object>): Integer {\n    return 0\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }
}
