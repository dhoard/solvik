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
 * Negative Phase 7 tests: mixed-type numeric operators, implicit widening or narrowing, invalid or
 * out-of-range explicit conversions, and invalid character literals each produce a source-located
 * diagnostic and no typed result.
 */
public final class SolvikNumericNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("numericneg.sol", text);
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

    // Mixed numeric operands are permitted only when they widen to a common type. A pair with no
    // least common widened numeric type has no legal comparison and is rejected (docs/LANGUAGE_SPEC.md
    // section 4): `Long` and `Float` share no widened type because `Long` loses precision as a Float.
    @Test
    public void mixedArithmeticWithNoCommonWidenedTypeIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x = 1L + 1.5f\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void mixedOrderingWithNoCommonWidenedTypeIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x = 1L < 1.5f\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void mixedEqualityWithNoCommonWidenedTypeIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x = 1L == 1.5f\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    // Precision-losing "widening" is rejected: the integral-to-floating relation holds only when
    // every source value is exactly representable in the target.
    @Test
    public void integerToFloatIsRejectedAsPrecisionLoss() {
        assertThat(first(checkFails("func f(): Unit {\n    val x: Float = 1\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void longToDoubleIsRejectedAsPrecisionLoss() {
        assertThat(first(checkFails("func f(): Unit {\n    val x: Double = 1L\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void longToFloatIsRejectedAsPrecisionLoss() {
        assertThat(first(checkFails("func f(): Unit {\n    val x: Float = 1L\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void floatingToIntegralIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x: Long = 1.5f\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void implicitNarrowingIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x: Integer = 1L\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void implicitFloatingNarrowingIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x: Float = 1.5\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void mixedIdentityIsRejected() {
        // `==` widens numeric operands, but `===` requires reference identity, which scalars lack,
        // and widening does not add an identity edge (docs/LANGUAGE_SPEC.md section 3).
        assertThat(first(checkFails("func f(): Unit {\n    val x = 1 === 1L\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    // Non-coercion boundary. Widening is only a value-to-typed-slot coercion; it must not leak into
    // subtyping, narrowing, `case`-label, `for-in`-bound, generic-inference, or join positions
    // (docs/LANGUAGE_SPEC.md section 4). Each case below uses an Integer where a Long is named, which
    // WOULD widen at a coercion site but must be rejected in these non-coercion positions.
    @Test
    public void caseLabelDoesNotWiden() {
        // A `case` label must be the same type as the scrutinee; widening a label would rewrite the case.
        assertThat(first(checkFails(
                        "func f(n: Long): Integer {\n    switch (n) {\n        case 1 {\n            return 0\n        }\n        default {\n            return 1\n        }\n    }\n}\n")).code())
                        .isEqualTo(DiagnosticCode.TYPE_CASE_LABEL_MISMATCH);
    }

    @Test
    public void rangeBoundDoesNotWiden() {
        // `for-in` range bounds are exactly Integer; a Long bound is not widened.
        assertThat(first(checkFails(
                        "func f(): Unit {\n    for (i in 1L...3L) {\n        println(i)\n    }\n}\n")).code())
                        .isEqualTo(DiagnosticCode.SEM_INVALID_RANGE_BOUND);
    }

    @Test
    public void joinDoesNotWidenNumericBranches() {
        // A branch join of Integer and Long is Number (nearest common declared supertype), never Long.
        // Asserting the rejected direction here: assigning that join back to Long must be a mismatch.
        assertThat(first(checkFails(
                        "func f(c: Boolean): Long {\n    return if (c) {\n        1\n    }\n    else {\n        1L\n    }\n}\n")).code())
                        .isEqualTo(DiagnosticCode.TYPE_RETURN_MISMATCH);
    }

    @Test
    public void bareTypeParameterTargetDoesNotWiden() {
        // An un-substituted type parameter T is not a concrete numeric type, so NumericTypes.widens
        // never fires for it; assigning an Integer to a bare generic slot is a nominal mismatch
        // (docs/LANGUAGE_SPEC.md section 4). Only a concrete substituted slot widens, exercised as a
        // positive case in SolvikNumericWideningTest.
        assertThat(first(checkFails(
                        "class Box<T> {\n    val value: T = 1\n}\n")).code())
                        .isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void convertingANonNumericValueIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x = Long(\"no\")\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_CONVERSION);
    }

    @Test
    public void callingANonNumericTypeIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x = String(1)\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_CONVERSION);
    }

    @Test
    public void callingTheAbstractNumberTypeIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x = Number(1)\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_CONVERSION);
    }

    @Test
    public void conversionArityMismatchIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x = Long(1, 2)\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void constantIntegralConversionOutOfRangeIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x = Byte(300)\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_CONVERSION_OUT_OF_RANGE);
    }

    @Test
    public void constantFloatingConversionOutOfRangeIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x = Byte(1e30)\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_CONVERSION_OUT_OF_RANGE);
    }

    @Test
    public void longLiteralOutOfRangeIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x = 9223372036854775808L\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_LONG_LITERAL_OUT_OF_RANGE);
    }

    @Test
    public void unsupportedCharacterEscapeIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x = '\\q'\n}\n")).code()).isEqualTo(DiagnosticCode.LEXER_INVALID_ESCAPE);
    }

    @Test
    public void characterArithmeticIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x = 'A' + 'B'\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void characterOrderingIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x = 'A' < 'B'\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }
}
