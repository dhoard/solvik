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
 * Negative Phase 15 semantic tests (docs/LANGUAGE_SPEC.md section 13): non-constant case labels,
 * label type mismatches, regex-case restrictions and invalid patterns, default placement, and the
 * switch-specific {@code break} rule.
 */
public final class SolvikSwitchNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("sneg.sol", text);
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
        assertEquals(DiagnosticCode.SEM_SWITCH_CASE_NOT_CONSTANT, first(checkFails("""
                func run(value: Int, other: Int): Unit {
                    switch (value) {
                        case other:
                            print("same")
                        default:
                            print("other")
                    }
                }
                """)).code());
    }

    @Test
    public void aComputedCaseLabelIsNotAConstant() {
        assertEquals(DiagnosticCode.SEM_SWITCH_CASE_NOT_CONSTANT, first(checkFails("""
                func run(value: Int): Unit {
                    switch (value) {
                        case 1 + 2:
                            print("three")
                        default:
                            print("other")
                    }
                }
                """)).code());
    }

    @Test
    public void aCaseLabelOfTheWrongTypeIsRejected() {
        assertEquals(DiagnosticCode.TYPE_CASE_LABEL_MISMATCH, first(checkFails("""
                func run(value: Int): Unit {
                    switch (value) {
                        case "a":
                            print("a")
                        default:
                            print("other")
                    }
                }
                """)).code());
    }

    @Test
    public void aRegexCaseRequiresAStringScrutinee() {
        assertEquals(DiagnosticCode.TYPE_REGEX_CASE_REQUIRES_STRING, first(checkFails("""
                func run(value: Int): Unit {
                    switch (value) {
                        case regex r#"\\d+"#:
                            print("number")
                        default:
                            print("other")
                    }
                }
                """)).code());
    }

    @Test
    public void anInvalidConstantRegexPatternIsRejected() {
        assertEquals(DiagnosticCode.TYPE_INVALID_REGEX_PATTERN, first(checkFails("""
                func run(input: String): Unit {
                    switch (input) {
                        case regex r#"("#:
                            print("bad")
                        default:
                            print("other")
                    }
                }
                """)).code());
    }

    @Test
    public void aDefaultFollowedByACaseIsRejected() {
        assertEquals(DiagnosticCode.SEM_SWITCH_DEFAULT_NOT_LAST, first(checkFails("""
                func run(value: Int): Unit {
                    switch (value) {
                        default:
                            print("other")
                        case 1:
                            print("one")
                    }
                }
                """)).code());
    }

    @Test
    public void twoDefaultsAreRejected() {
        DiagnosticBag bag = checkFails("""
                func run(value: Int): Unit {
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
        assertTrue(hasCode(bag, DiagnosticCode.SEM_SWITCH_DUPLICATE_DEFAULT));
    }

    @Test
    public void aBreakDirectlyInACaseInsideALoopIsRejected() {
        assertEquals(DiagnosticCode.SEM_BREAK_IN_SWITCH_CASE, first(checkFails("""
                func run(value: Int): Unit {
                    while (true) {
                        switch (value) {
                            case 1:
                                break
                            default:
                                print("other")
                        }
                    }
                }
                """)).code());
    }

    @Test
    public void aBreakDirectlyInACaseWithoutALoopIsRejected() {
        assertEquals(DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP, first(checkFails("""
                func run(value: Int): Unit {
                    switch (value) {
                        case 1:
                            break
                        default:
                            print("other")
                    }
                }
                """)).code());
    }

    @Test
    public void aContinueDirectlyInACaseWithoutALoopIsRejected() {
        assertEquals(DiagnosticCode.SEM_LOOP_CONTROL_OUTSIDE_LOOP, first(checkFails("""
                func run(value: Int): Unit {
                    switch (value) {
                        case 1:
                            continue
                        default:
                            print("other")
                    }
                }
                """)).code());
    }

    @Test
    public void aCallIsNotAConstantCaseLabel() {
        assertEquals(DiagnosticCode.SEM_SWITCH_CASE_NOT_CONSTANT, first(checkFails("""
                func compute(): Int {
                    return 1
                }
                func run(value: Int): Unit {
                    switch (value) {
                        case compute():
                            print("one")
                        default:
                            print("other")
                    }
                }
                """)).code());
    }

    @Test
    public void aNullLabelDoesNotMatchANonNullableScrutinee() {
        assertEquals(DiagnosticCode.TYPE_CASE_LABEL_MISMATCH, first(checkFails("""
                func run(value: Int): Unit {
                    switch (value) {
                        case null:
                            print("none")
                        default:
                            print("other")
                    }
                }
                """)).code());
    }
}
