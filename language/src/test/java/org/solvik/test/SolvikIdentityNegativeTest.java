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
 * Negative semantic tests for the reference-identity operators (docs/LANGUAGE_SPEC.md section 3).
 * An identity operand must be assignment-compatible with the other and at least one operand must
 * have a Solvik allocation identity. Scalars, {@code Unit}, enums, regex values, {@code Any},
 * unbounded type parameters, and bare null are all rejected even though several of
 * them are assignment-compatible. The failures must happen during semantic analysis, before any
 * lowering or execution.
 */
public final class SolvikIdentityNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("identityneg.sol", text);
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
    public void integralScalarsHaveNoIdentity() {
        assertThat(first(checkFails("func f(): Boolean {\n    return 1 === 1\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
        assertThat(first(checkFails("func f(): Boolean {\n    return 1L === 1L\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
        assertThat(first(checkFails("func f(): Boolean {\n    return Byte(1) === Byte(1)\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
        assertThat(first(checkFails("func f(): Boolean {\n    return Short(1) === Short(1)\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
    }

    @Test
    public void floatingScalarsHaveNoIdentity() {
        assertThat(first(checkFails("func f(): Boolean {\n    return 1.5 === 1.5\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
        assertThat(first(checkFails("func f(): Boolean {\n    return 1.5f === 1.5f\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
    }

    @Test
    public void booleanCharacterAndStringHaveNoIdentity() {
        assertThat(first(checkFails("func f(): Boolean {\n    return true === false\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
        assertThat(first(checkFails("func f(): Boolean {\n    return 'a' === 'b'\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
        assertThat(first(checkFails("func f(): Boolean {\n    return \"a\" === \"b\"\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
    }

    @Test
    public void unitHasNoIdentity() {
        assertThat(first(checkFails("func g(): Unit {\n}\n\nfunc f(): Boolean {\n    return g() === g()\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
    }

    @Test
    public void enumValuesHaveNoIdentity() {
        String text = """
                enum Color {
                    Red
                    Blue
                }

                func f(): Boolean {
                    return Color.Red === Color.Red
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
    }

    @Test
    public void regexValuesHaveNoIdentity() {
        String text = """
                func f(): Boolean {
                    return Regex("a") === Regex("a")
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
    }

    @Test
    public void anyHasNoIdentity() {
        assertThat(first(checkFails("func f(a: Any, b: Any): Boolean {\n    return a === b\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
        assertThat(first(checkFails("func f(a: Any?, b: Any?): Boolean {\n    return a === b\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
    }

    @Test
    public void unboundedTypeParameterHasNoIdentity() {
        String text = """
                func same<T>(a: T, b: T): Boolean {
                    return a === b
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
    }

    @Test
    public void nullAgainstNullHasNoIdentityAnchor() {
        assertThat(first(checkFails("func f(): Boolean {\n    return null === null\n}\n")).code()).isEqualTo(DiagnosticCode.TYPE_IDENTITY_OPERANDS);
    }

    @Test
    public void nonNullReferenceAgainstNullIsNotComparable() {
        String text = """
                class Point {
                }

                func f(): Boolean {
                    return Point() === null
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void unrelatedNominalTypesAreNotComparable() {
        String text = """
                class Point {
                }

                class Other {
                }

                func f(): Boolean {
                    return Point() === Other()
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_OPERANDS);
    }

    @Test
    public void everyIdentityDiagnosticIsStableAndLocated() {
        DiagnosticBag bag = checkFails("func f(): Boolean {\n    return 1 === 1\n}\n");
        Diagnostic diagnostic = first(bag);
        assertThat(diagnostic.code().stableCode()).isEqualTo("SOLV-TYPE-039");
        assertThat(diagnostic.span().startOffset()).isGreaterThanOrEqualTo(0);
        assertThat(bag.all()).allSatisfy(d -> assertThat(d.message()).doesNotContain("java.").doesNotContain("Object.equals"));
    }
}
