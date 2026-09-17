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

/**
 * Negative Phase 7 tests: mixed-type numeric operators, implicit widening or narrowing, invalid or
 * out-of-range explicit conversions, and invalid character literals each produce a source-located
 * diagnostic and no typed result.
 */
public final class SolvikNumericNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("numericneg.sol", text);
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
    public void mixedNumericArithmeticIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, first(checkFails("func f(): Unit {\n    val x = 1 + 1L\n}\n")).code());
    }

    @Test
    public void mixedNumericEqualityIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, first(checkFails("func f(): Unit {\n    val x = 1 == 1L\n}\n")).code());
    }

    @Test
    public void implicitWideningIsRejected() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails("func f(): Unit {\n    val x: Long = 1\n}\n")).code());
    }

    @Test
    public void implicitNarrowingIsRejected() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails("func f(): Unit {\n    val x: Int = 1L\n}\n")).code());
    }

    @Test
    public void implicitFloatingNarrowingIsRejected() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails("func f(): Unit {\n    val x: Double = 1.5f\n}\n")).code());
    }

    @Test
    public void convertingANonNumericValueIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_CONVERSION, first(checkFails("func f(): Unit {\n    val x = Long(\"no\")\n}\n")).code());
    }

    @Test
    public void callingANonNumericTypeIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_CONVERSION, first(checkFails("func f(): Unit {\n    val x = String(1)\n}\n")).code());
    }

    @Test
    public void callingTheAbstractNumberTypeIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_CONVERSION, first(checkFails("func f(): Unit {\n    val x = Number(1)\n}\n")).code());
    }

    @Test
    public void conversionArityMismatchIsRejected() {
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(checkFails("func f(): Unit {\n    val x = Long(1, 2)\n}\n")).code());
    }

    @Test
    public void constantIntegralConversionOutOfRangeIsRejected() {
        assertEquals(DiagnosticCode.TYPE_CONVERSION_OUT_OF_RANGE, first(checkFails("func f(): Unit {\n    val x = Byte(300)\n}\n")).code());
    }

    @Test
    public void constantFloatingConversionOutOfRangeIsRejected() {
        assertEquals(DiagnosticCode.TYPE_CONVERSION_OUT_OF_RANGE, first(checkFails("func f(): Unit {\n    val x = Byte(1e30)\n}\n")).code());
    }

    @Test
    public void longLiteralOutOfRangeIsRejected() {
        assertEquals(DiagnosticCode.TYPE_LONG_LITERAL_OUT_OF_RANGE, first(checkFails("func f(): Unit {\n    val x = 9223372036854775808L\n}\n")).code());
    }

    @Test
    public void unsupportedCharacterEscapeIsRejected() {
        assertEquals(DiagnosticCode.LEXER_INVALID_ESCAPE, first(checkFails("func f(): Unit {\n    val x = '\\q'\n}\n")).code());
    }

    @Test
    public void characterArithmeticIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, first(checkFails("func f(): Unit {\n    val x = 'A' + 'B'\n}\n")).code());
    }

    @Test
    public void characterOrderingIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_OPERANDS, first(checkFails("func f(): Unit {\n    val x = 'A' < 'B'\n}\n")).code());
    }
}
