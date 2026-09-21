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

    @Test
    public void mixedNumericArithmeticIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x = 1 + 1L\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void mixedNumericEqualityIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x = 1 == 1L\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void implicitWideningIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x: Long = 1\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void implicitNarrowingIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x: Integer = 1L\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void implicitFloatingNarrowingIsRejected() {
        assertThat(first(checkFails("func f(): Unit {\n    val x: Double = 1.5f\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
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
