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

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.IncludeResolutionResult;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/** Negative semantic tests for expression-oriented constructs (docs/LANGUAGE_SPEC.md section 21). */
public final class SolvikExpressionOrientedNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("exprneg.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must fail: " + text + " -> " + result.diagnostics().all()).isFalse();
        assertThat(result.program().isEmpty()).as("failed analysis must expose no program").isTrue();
        for (Diagnostic diagnostic : result.diagnostics().all()) {
            assertThat(diagnostic.span().endOffset() <= text.length()).as("span within source bounds: " + diagnostic.span()).isTrue();
        }
        return result.diagnostics();
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
    public void emptyBlockExpressionIsRejected() {
        assertThat(hasCode(checkFails("""
                func f(): Integer {
                    val x = {}
                    return x
                }
                """), DiagnosticCode.SEM_BLOCK_RESULT_REQUIRED)).isTrue();
    }

    @Test
    public void blockEndingInLocalDeclarationIsRejected() {
        assertThat(hasCode(checkFails("""
                func f(): Integer {
                    val x = {
                        val local = 1
                    }
                    return x
                }
                """), DiagnosticCode.SEM_BLOCK_RESULT_REQUIRED)).isTrue();
    }

    @Test
    public void blockEndingInAssignmentIsRejected() {
        assertThat(hasCode(checkFails("""
                func f(): Integer {
                    val x = {
                        var local = 1
                        local = 2
                    }
                    return x
                }
                """), DiagnosticCode.SEM_BLOCK_RESULT_REQUIRED)).isTrue();
    }

    @Test
    public void expressionIfWithoutElseIsRejected() {
        DiagnosticBag bag = checkFails("""
                func f(flag: Boolean): Integer {
                    val x = if (flag) { 1 }
                    return x
                }
                """);
        assertThat(hasCode(bag, DiagnosticCode.SEM_IF_EXPRESSION_MISSING_ELSE)).isTrue();
        assertThat(hasCode(bag, DiagnosticCode.TYPE_BRANCH_RESULT)).as("missing else must not cascade into a join error").isFalse();
    }

    @Test
    public void expressionSwitchWithoutDefaultIsRejected() {
        DiagnosticBag bag = checkFails("""
                func f(value: Integer): String {
                    val x = switch (value) {
                        case 1:
                            "one"
                    }
                    return x
                }
                """);
        assertThat(hasCode(bag, DiagnosticCode.SEM_SWITCH_EXPRESSION_MISSING_DEFAULT)).isTrue();
        assertThat(hasCode(bag, DiagnosticCode.TYPE_BRANCH_RESULT)).as("missing default must not cascade into a join error").isFalse();
    }

    @Test
    public void nonBooleanIfExpressionConditionIsRejected() {
        assertThat(hasCode(checkFails("""
                func f(): Integer {
                    val x = if (1) { 1 } else { 2 }
                    return x
                }
                """), DiagnosticCode.TYPE_CONDITION_NOT_BOOLEAN)).isTrue();
    }

    @Test
    public void tailResultNotAssignableToDeclaredTypeIsRejected() {
        assertThat(hasCode(checkFails("""
                func f(): Integer {
                    val x: String = { 1 }
                    return 0
                }
                """), DiagnosticCode.TYPE_MISMATCH)).isTrue();
    }

    @Test
    public void expressionSwitchCaseWithoutTailResultIsRejected() {
        assertThat(hasCode(checkFails("""
                func f(value: Integer): Integer {
                    val x = switch (value) {
                        case 1:
                            val local = 1
                        default:
                            0
                    }
                    return x
                }
                """), DiagnosticCode.SEM_BLOCK_RESULT_REQUIRED)).isTrue();
    }

    @Test
    public void duplicateDefaultInExpressionSwitchIsRejected() {
        assertThat(hasCode(checkFails("""
                func f(value: Integer): Integer {
                    val x = switch (value) {
                        case 1:
                            1
                        default:
                            2
                        default:
                            3
                    }
                    return x
                }
                """), DiagnosticCode.SEM_SWITCH_DUPLICATE_DEFAULT)).isTrue();
    }

    @Test
    public void defaultNotLastInExpressionSwitchIsRejected() {
        assertThat(hasCode(checkFails("""
                func f(value: Integer): Integer {
                    val x = switch (value) {
                        default:
                            0
                        case 1:
                            1
                    }
                    return x
                }
                """), DiagnosticCode.SEM_SWITCH_DEFAULT_NOT_LAST)).isTrue();
    }

    @Test
    public void misplacedDefaultInExpressionSwitchReportsASingleDiagnostic() {
        DiagnosticBag bag = checkFails("""
                func f(value: Integer): Integer {
                    val x = switch (value) {
                        default:
                            0
                        case 1:
                            1
                        case 2:
                            2
                    }
                    return x
                }
                """);
        assertThat(bag.all()).extracting(Diagnostic::code).containsExactly(
                DiagnosticCode.SEM_SWITCH_DEFAULT_NOT_LAST);
    }

    @Test
    public void branchLocalNameDoesNotLeakOutOfTheBlock() {
        assertThat(hasCode(checkFails("""
                func f(): Integer {
                    val x = {
                        val hidden = 1
                        hidden
                    }
                    return hidden
                }
                """), DiagnosticCode.RESOL_UNKNOWN_NAME)).isTrue();
    }

    @Test
    public void ambiguousBranchResultJoinIsRejected() {
        assertThat(hasCode(checkFails("""
                interface A {}
                interface B {}
                class C implements A, B {}
                class D implements A, B {}

                func f(flag: Boolean): Any {
                    return if (flag) { C() } else { D() }
                }
                """), DiagnosticCode.TYPE_BRANCH_RESULT)).isTrue();
    }

    @Test
    public void functionStillRequiresAnExplicitReturn() {
        DiagnosticBag bag = checkFails("""
                func f(): Integer {
                    42
                }
                """);
        assertThat(hasCode(bag, DiagnosticCode.SEM_VALUE_EXPRESSION_STATEMENT) || hasCode(bag, DiagnosticCode.TYPE_MISSING_RETURN_PATH))
                .as("a final expression is not an implicit return")
                .isTrue();
    }

    @Test
    public void matchBranchBlockWithoutTailResultIsRejected() {
        assertThat(hasCode(checkFails("""
                enum Result {
                    Ok(Integer)
                    Error(String)
                }

                func f(result: Result): Integer {
                    return match result {
                        Ok(value) => {
                            val local = 1
                        }
                        Error(message) => 0
                    }
                }
                """), DiagnosticCode.SEM_BLOCK_RESULT_REQUIRED)).isTrue();
    }

    @Test
    public void diagnosticInAnIncludedFilePointsToThatFile() {
        IncludeResolutionResult resolved = VirtualIncludeFiles.resolve("root.sol", Map.of(
                "root.sol", "include \"lib.sol\"\n",
                "lib.sol", "func bad(): Integer {\n    val x = {\n        val local = 1\n    }\n    return x\n}\n"));
        assertThat(resolved.isSuccess()).as("resolution must succeed: " + resolved.diagnostics().all()).isTrue();
        SemanticResult result = SolvikSemanticAnalyzer.analyze(resolved.requireUnit(), resolved.itemScopes());
        assertThat(result.isSuccess()).isFalse();
        Diagnostic diagnostic = result.diagnostics().all().stream()
                .filter(d -> d.code() == DiagnosticCode.SEM_BLOCK_RESULT_REQUIRED)
                .findFirst()
                .orElseThrow();
        assertThat(diagnostic.span().sourceId()).as("the failing block is in the included physical file").isNotZero();
    }

    @Test
    public void missingElseInsideABlockTailStillReportsTheDedicatedDiagnostic() {
        assertThat(hasCode(checkFails("""
                func f(flag: Boolean): Integer {
                    val x = {
                        if (flag) {
                            1
                        }
                    }
                    return x
                }
                """), DiagnosticCode.SEM_IF_EXPRESSION_MISSING_ELSE)).isTrue();
    }

    @Test
    public void missingDefaultInsideABlockTailStillReportsTheDedicatedDiagnostic() {
        assertThat(hasCode(checkFails("""
                func f(value: Integer): Integer {
                    val x = {
                        switch (value) {
                            case 1:
                                1
                        }
                    }
                    return x
                }
                """), DiagnosticCode.SEM_SWITCH_EXPRESSION_MISSING_DEFAULT)).isTrue();
    }

    @Test
    public void aValueBlockTailWithAnUnresolvedNameStillReportsThatError() {
        assertThat(hasCode(checkFails("""
                func f(): Integer {
                    val x = { missing }
                    return 0
                }
                """), DiagnosticCode.RESOL_UNKNOWN_NAME)).isTrue();
    }
}
