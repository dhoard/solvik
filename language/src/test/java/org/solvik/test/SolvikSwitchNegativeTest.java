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
 * Negative Phase 15 semantic tests (docs/LANGUAGE_SPEC.md section 13): non-constant case labels,
 * label type mismatches, regex-case restrictions and invalid patterns, default placement, and the
 * switch-specific {@code break} rule.
 */
public final class SolvikSwitchNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("sneg.sol", text);
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

    private static boolean hasCode(DiagnosticBag bag, DiagnosticCode code) {
        for (Diagnostic diagnostic : bag.all()) {
            if (diagnostic.code() == code) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void aVariableCaseLabelIsNotAConstant() {
        assertThat(first(checkFails("""
                func run(value: Integer, other: Integer): Unit {
                    switch (value) {
                        case other:
                            print("same")
                        default:
                            print("other")
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_SWITCH_CASE_NOT_CONSTANT);
    }

    @Test
    public void aComputedCaseLabelIsNotAConstant() {
        assertThat(first(checkFails("""
                func run(value: Integer): Unit {
                    switch (value) {
                        case 1 + 2:
                            print("three")
                        default:
                            print("other")
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_SWITCH_CASE_NOT_CONSTANT);
    }

    @Test
    public void aCaseLabelOfTheWrongTypeIsRejected() {
        assertThat(first(checkFails("""
                func run(value: Integer): Unit {
                    switch (value) {
                        case "a":
                            print("a")
                        default:
                            print("other")
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_CASE_LABEL_MISMATCH);
    }

    @Test
    public void aRegexCaseRequiresAStringScrutinee() {
        assertThat(first(checkFails("""
                func run(value: Integer): Unit {
                    switch (value) {
                        case regex r#"\\d+"#:
                            print("number")
                        default:
                            print("other")
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_REGEX_CASE_REQUIRES_STRING);
    }

    /**
     * A nullable `String?` scrutinee is not a `String` switch value, so a regex case over it is a
     * compile-time error rather than a runtime failure on a null scrutinee
     * (docs/LANGUAGE_SPEC.md sections 13 and 19: compile-time correctness).
     */
    @Test
    public void aRegexCaseRequiresANonNullableStringScrutinee() {
        assertThat(first(checkFails("""
                func run(input: String?): Unit {
                    switch (input) {
                        case regex r#"^\\d+$"#:
                            print("number")
                        default:
                            print("other")
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_REGEX_CASE_REQUIRES_STRING);
    }

    /** The expression `switch` shares the regex-case rule, so a nullable scrutinee is rejected there too. */
    @Test
    public void aRegexCaseInASwitchExpressionRequiresANonNullableStringScrutinee() {
        assertThat(first(checkFails("""
                func run(input: String?): String {
                    return switch (input) {
                        case regex r#"^\\d+$"#:
                            "number"
                        default:
                            "other"
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_REGEX_CASE_REQUIRES_STRING);
    }

    @Test
    public void anInvalidConstantRegexPatternIsRejected() {
        assertThat(first(checkFails("""
                func run(input: String): Unit {
                    switch (input) {
                        case regex r#"("#:
                            print("bad")
                        default:
                            print("other")
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_INVALID_REGEX_PATTERN);
    }

    @Test
    public void aDefaultFollowedByACaseIsRejected() {
        assertThat(first(checkFails("""
                func run(value: Integer): Unit {
                    switch (value) {
                        default:
                            print("other")
                        case 1:
                            print("one")
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_SWITCH_DEFAULT_NOT_LAST);
    }

    @Test
    public void twoDefaultsAreRejected() {
        DiagnosticBag bag = checkFails("""
                func run(value: Integer): Unit {
                    switch (value) {
                        case 1:
                            print("one")
                        default:
                            print("first")
                        default:
                            print("second")
                    }
                }
                """);
        assertThat(hasCode(bag, DiagnosticCode.SEM_SWITCH_DUPLICATE_DEFAULT)).isTrue();
    }

    @Test
    public void aBreakDirectlyInACaseInsideALoopIsRejected() {
        assertThat(first(checkFails("""
                func run(value: Integer): Unit {
                    while (true) {
                        switch (value) {
                            case 1:
                                break
                            default:
                                print("other")
                        }
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_BREAK_IN_SWITCH_CASE);
    }

    @Test
    public void aBreakDirectlyInACaseWithoutALoopIsRejected() {
        assertThat(first(checkFails("""
                func run(value: Integer): Unit {
                    switch (value) {
                        case 1:
                            break
                        default:
                            print("other")
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP);
    }

    @Test
    public void aContinueDirectlyInACaseWithoutALoopIsRejected() {
        assertThat(first(checkFails("""
                func run(value: Integer): Unit {
                    switch (value) {
                        case 1:
                            continue
                        default:
                            print("other")
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP);
    }

    @Test
    public void aCallIsNotAConstantCaseLabel() {
        assertThat(first(checkFails("""
                func compute(): Integer {
                    return 1
                }
                func run(value: Integer): Unit {
                    switch (value) {
                        case compute():
                            print("one")
                        default:
                            print("other")
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_SWITCH_CASE_NOT_CONSTANT);
    }

    @Test
    public void aNullLabelDoesNotMatchANonNullableScrutinee() {
        assertThat(first(checkFails("""
                func run(value: Integer): Unit {
                    switch (value) {
                        case null:
                            print("none")
                        default:
                            print("other")
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_CASE_LABEL_MISMATCH);
    }

    @Test
    public void misplacedDefaultReportsASingleDiagnostic() {
        DiagnosticBag bag = checkFails("""
                func run(value: Integer): Unit {
                    switch (value) {
                        default:
                            print("other")
                        case 1:
                            print("one")
                        case 2:
                            print("two")
                    }
                }
                """);
        assertThat(bag.all()).extracting(Diagnostic::code).containsExactly(
                DiagnosticCode.SEM_SWITCH_DEFAULT_NOT_LAST);
    }
}
