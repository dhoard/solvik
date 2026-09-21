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
 * Negative Phase 13 semantic tests (docs/LANGUAGE_SPEC.md section 12): non-exhaustive matches,
 * unreachable or duplicate branches, unknown variants, arity and type mismatches, and an
 * ill-typed branch result set.
 */
public final class SolvikMatchNegativeTest {

    private static final String RESULT = """
            enum Result {
                Ok(Integer)
                Error(String)
            }
            """;

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("mneg.sol", text);
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
    public void aMissingEnumVariantIsNotExhaustive() {
        assertThat(first(checkFails(RESULT + """
                func message(result: Result): String {
                    return match result {
                        Ok(value) => "ok"
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_MATCH_NOT_EXHAUSTIVE);
    }

    @Test
    public void aMissingSealedSubtypeIsNotExhaustive() {
        assertThat(first(checkFails("""
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
                """)).code()).isEqualTo(DiagnosticCode.SEM_MATCH_NOT_EXHAUSTIVE);
    }

    @Test
    public void anEmptyMatchOverANonClosedTypeIsNotExhaustive() {
        assertThat(first(checkFails("""
                func label(value: Integer): Integer {
                    return match value {
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_MATCH_NOT_EXHAUSTIVE);
    }

    @Test
    public void aNullableEnumWithoutAWildcardDoesNotCoverNull() {
        assertThat(first(checkFails(RESULT + """
                func message(result: Result?): String {
                    return match result {
                        Ok(value) => "ok"
                        Error(error) => "error"
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_MATCH_NOT_EXHAUSTIVE);
    }

    @Test
    public void aTypedBindingThatCoversNonNullValuesStillMissesNull() {
        assertThat(first(checkFails(RESULT + """
                func message(result: Result?): String {
                    return match result {
                        full: Result => "result"
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_MATCH_NOT_EXHAUSTIVE);
    }

    @Test
    public void aDuplicateValueLessVariantIsUnreachable() {
        assertThat(first(checkFails(RESULT + """
                func message(result: Result): String {
                    return match result {
                        Ok(value) => "ok"
                        Error(error) => "error"
                        Ok(other) => "again"
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_MATCH_UNREACHABLE_PATTERN);
    }

    @Test
    public void aBranchAfterAWildcardIsUnreachable() {
        assertThat(first(checkFails(RESULT + """
                func message(result: Result): String {
                    return match result {
                        _ => "other"
                        Ok(value) => "ok"
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_MATCH_UNREACHABLE_PATTERN);
    }

    @Test
    public void anUnknownVariantIsRejected() {
        assertThat(first(checkFails(RESULT + """
                func message(result: Result): String {
                    return match result {
                        Missing(value) => "missing"
                        _ => "other"
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void aValueCarryingVariantUsedBareHasTheWrongArity() {
        assertThat(first(checkFails(RESULT + """
                func message(result: Result): String {
                    return match result {
                        Ok => "ok"
                        Error(error) => "error"
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void aValueLessVariantGivenArgumentsHasTheWrongArity() {
        assertThat(first(checkFails("""
                enum Color {
                    Red
                }
                func name(color: Color): String {
                    return match color {
                        Red(value) => "red"
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void anEnumVariantPatternOnANonEnumIsRejected() {
        assertThat(first(checkFails("""
                func label(value: Integer): Integer {
                    return match value {
                        Some(value) => 1
                        _ => 0
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_MATCH_PATTERN);
    }

    @Test
    public void aBindingPatternTypeUnrelatedToTheScrutineeIsRejected() {
        assertThat(first(checkFails(RESULT + """
                class Other {
                }
                func message(result: Result): String {
                    return match result {
                        other: Other => "other"
                        _ => "none"
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_MATCH_PATTERN);
    }

    @Test
    public void duplicateBindingNamesInOneBranchAreRejected() {
        assertThat(first(checkFails("""
                enum Pair {
                    Both(Integer, Integer)
                }
                func first(pair: Pair): Integer {
                    return match pair {
                        Both(value, value) => value
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void aNullableBindingPatternTypeIsRejected() {
        assertThat(first(checkFails("""
                sealed class Shape {
                }
                func name(shape: Shape): String {
                    return match shape {
                        maybe: Shape? => "maybe"
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_TYPE_OPERAND);
    }

    @Test
    public void anErasedGenericBindingPatternTypeIsRejected() {
        assertThat(first(checkFails("""
                sealed class Shape {
                }
                func name(shape: Shape): String {
                    return match shape {
                        box: List<Integer> => "box"
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ERASED_TYPE_TEST);
    }

    @Test
    public void branchResultsWithNoNearestCommonSupertypeAreIllTyped() {
        assertThat(first(checkFails("""
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
                """)).code()).isEqualTo(DiagnosticCode.TYPE_MATCH_RESULT);
    }
}
