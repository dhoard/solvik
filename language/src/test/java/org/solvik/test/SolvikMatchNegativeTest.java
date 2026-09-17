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
 * Negative Phase 13 semantic tests (docs/LANGUAGE_SPEC.md section 12): non-exhaustive matches,
 * unreachable or duplicate branches, unknown variants, arity and type mismatches, and an
 * ill-typed branch result set.
 */
public final class SolvikMatchNegativeTest {

    private static final String RESULT = """
            enum Result {
                Ok(Int)
                Error(String)
            }
            """;

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("mneg.sol", text);
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
    public void aMissingEnumVariantIsNotExhaustive() {
        assertEquals(DiagnosticCode.SEM_MATCH_NOT_EXHAUSTIVE, first(checkFails(RESULT + """
                func message(result: Result): String {
                    return match result {
                        Ok(value) => "ok"
                    }
                }
                """)).code());
    }

    @Test
    public void aMissingSealedSubtypeIsNotExhaustive() {
        assertEquals(DiagnosticCode.SEM_MATCH_NOT_EXHAUSTIVE, first(checkFails("""
                sealed class Shape {
                }
                class Circle extends Shape {
                }
                class Square extends Shape {
                }
                func name(shape: Shape): String {
                    return match shape {
                        circle: Circle => "circle"
                    }
                }
                """)).code());
    }

    @Test
    public void anEmptyMatchOverANonClosedTypeIsNotExhaustive() {
        assertEquals(DiagnosticCode.SEM_MATCH_NOT_EXHAUSTIVE, first(checkFails("""
                func label(value: Int): Int {
                    return match value {
                    }
                }
                """)).code());
    }

    @Test
    public void aNullableEnumWithoutAWildcardDoesNotCoverNull() {
        assertEquals(DiagnosticCode.SEM_MATCH_NOT_EXHAUSTIVE, first(checkFails(RESULT + """
                func message(result: Result?): String {
                    return match result {
                        Ok(value) => "ok"
                        Error(error) => "error"
                    }
                }
                """)).code());
    }

    @Test
    public void aTypedBindingThatCoversNonNullValuesStillMissesNull() {
        assertEquals(DiagnosticCode.SEM_MATCH_NOT_EXHAUSTIVE, first(checkFails(RESULT + """
                func message(result: Result?): String {
                    return match result {
                        full: Result => "result"
                    }
                }
                """)).code());
    }

    @Test
    public void aDuplicateValueLessVariantIsUnreachable() {
        assertEquals(DiagnosticCode.SEM_MATCH_UNREACHABLE_PATTERN, first(checkFails(RESULT + """
                func message(result: Result): String {
                    return match result {
                        Ok(value) => "ok"
                        Error(error) => "error"
                        Ok(other) => "again"
                    }
                }
                """)).code());
    }

    @Test
    public void aBranchAfterAWildcardIsUnreachable() {
        assertEquals(DiagnosticCode.SEM_MATCH_UNREACHABLE_PATTERN, first(checkFails(RESULT + """
                func message(result: Result): String {
                    return match result {
                        _ => "other"
                        Ok(value) => "ok"
                    }
                }
                """)).code());
    }

    @Test
    public void anUnknownVariantIsRejected() {
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_MEMBER, first(checkFails(RESULT + """
                func message(result: Result): String {
                    return match result {
                        Missing(value) => "missing"
                        _ => "other"
                    }
                }
                """)).code());
    }

    @Test
    public void aValueCarryingVariantUsedBareHasTheWrongArity() {
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(checkFails(RESULT + """
                func message(result: Result): String {
                    return match result {
                        Ok => "ok"
                        Error(error) => "error"
                    }
                }
                """)).code());
    }

    @Test
    public void aValueLessVariantGivenArgumentsHasTheWrongArity() {
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(checkFails("""
                enum Color {
                    Red
                }
                func name(color: Color): String {
                    return match color {
                        Red(value) => "red"
                    }
                }
                """)).code());
    }

    @Test
    public void anEnumVariantPatternOnANonEnumIsRejected() {
        assertEquals(DiagnosticCode.TYPE_MATCH_PATTERN, first(checkFails("""
                func label(value: Int): Int {
                    return match value {
                        Some(value) => 1
                        _ => 0
                    }
                }
                """)).code());
    }

    @Test
    public void aBindingPatternTypeUnrelatedToTheScrutineeIsRejected() {
        assertEquals(DiagnosticCode.TYPE_MATCH_PATTERN, first(checkFails(RESULT + """
                class Other {
                }
                func message(result: Result): String {
                    return match result {
                        other: Other => "other"
                        _ => "none"
                    }
                }
                """)).code());
    }

    @Test
    public void duplicateBindingNamesInOneBranchAreRejected() {
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, first(checkFails("""
                enum Pair {
                    Both(Int, Int)
                }
                func first(pair: Pair): Int {
                    return match pair {
                        Both(value, value) => value
                    }
                }
                """)).code());
    }

    @Test
    public void aNullableBindingPatternTypeIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_TYPE_OPERAND, first(checkFails("""
                sealed class Shape {
                }
                func name(shape: Shape): String {
                    return match shape {
                        maybe: Shape? => "maybe"
                    }
                }
                """)).code());
    }

    @Test
    public void anErasedGenericBindingPatternTypeIsRejected() {
        assertEquals(DiagnosticCode.TYPE_ERASED_TYPE_TEST, first(checkFails("""
                sealed class Shape {
                }
                func name(shape: Shape): String {
                    return match shape {
                        box: List<Int> => "box"
                    }
                }
                """)).code());
    }

    @Test
    public void branchResultsWithNoNearestCommonSupertypeAreIllTyped() {
        assertEquals(DiagnosticCode.TYPE_MATCH_RESULT, first(checkFails("""
                interface A {
                }
                interface B {
                }
                sealed class Root {
                }
                class Left extends Root implements A, B {
                }
                class Right extends Root implements A, B {
                }
                func pick(root: Root): Root {
                    return match root {
                        left: Left => left
                        right: Right => right
                    }
                }
                """)).code());
    }
}
