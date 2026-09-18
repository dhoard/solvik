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
 * Negative Phase 7 semantic tests: extending final classes, accidental or invalid overrides,
 * inheritance cycles, invalid superclasses, and misplaced or missing {@code super} uses each
 * produce a source-located diagnostic and no typed result.
 */
public final class SolvikInheritanceNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("inheritneg.sol", text);
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
    public void extendingAFinalClassIsRejected() {
        Diagnostic diagnostic = first(checkFails("class A {\n}\nclass B extends A {\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_EXTEND_FINAL);
    }

    @Test
    public void accidentalOverrideWithoutTheKeywordIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    open func f(): Int {
                        return 1
                    }
                }
                class B extends A {
                    func f(): Int {
                        return 2
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_ACCIDENTAL_OVERRIDE);
    }

    @Test
    public void overridingAFinalMethodIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    func f(): Int {
                        return 1
                    }
                }
                class B extends A {
                    override func f(): Int {
                        return 2
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_OVERRIDE_FINAL);
    }

    @Test
    public void overrideWithoutAnInheritedMethodIsRejected() {
        Diagnostic diagnostic = first(checkFails("class B {\n    override func f(): Int {\n        return 2\n    }\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_OVERRIDE_WITHOUT_SUPER);
    }

    @Test
    public void covariantReturnTypesAreAcceptedButUnrelatedOnesAreRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    open func f(): Int {
                        return 1
                    }
                }
                class B extends A {
                    override func f(): String {
                        return "x"
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_OVERRIDE_SIGNATURE);
    }

    @Test
    public void mismatchedOverrideParameterTypesAreRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    open func f(x: Int): Int {
                        return x
                    }
                }
                class B extends A {
                    override func f(x: String): Int {
                        return 1
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_OVERRIDE_SIGNATURE);
    }

    @Test
    public void inheritanceCyclesAreRejected() {
        Diagnostic diagnostic = first(checkFails("class A extends B {\n}\nclass B extends A {\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_INHERITANCE_CYCLE);
    }

    @Test
    public void extendingANonClassBuiltinIsRejected() {
        Diagnostic diagnostic = first(checkFails("class A extends Int {\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_INVALID_SUPERCLASS);
    }

    @Test
    public void superOutsideAClassIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Int {\n    return super.g()\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_SUPER_OUTSIDE_CLASS);
    }

    @Test
    public void superWithoutASuperclassIsRejected() {
        Diagnostic diagnostic = first(checkFails("class A {\n    func f(): Int {\n        return super.g()\n    }\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_SUPER_OUTSIDE_CLASS);
    }

    @Test
    public void bareSuperAsAValueIsRejected() {
        Diagnostic diagnostic = first(checkFails("open class A {\n}\nclass B extends A {\n    func f(): A {\n        val x = super;\n        return x;\n    }\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_SUPER_AS_VALUE);
    }

    @Test
    public void superCallMustBeTheFirstConstructorStatement() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    A(x: Int) {
                    }
                }
                class B extends A {
                    B() {
                        val y = 1
                        super(1)
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_SUPER_CALL_PLACEMENT);
    }

    @Test
    public void missingExplicitSuperCallIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    A(x: Int) {
                    }
                }
                class B extends A {
                    B() {
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_MISSING_SUPER_INIT);
    }

    @Test
    public void subclassWithoutConstructorCannotSatisfyASuperclassRequiringArguments() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    A(x: Int) {
                    }
                }
                class B extends A {
                    val y: Int = 1
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_MISSING_SUPER_INIT_IMPLICIT);
    }

    @Test
    public void redeclaringAnInheritedPropertyIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    val x: Int = 1
                }
                class B extends A {
                    val x: Int = 2
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void writingAnInheritedValPropertyIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                open class A {
                    val x: Int = 1
                }
                class B extends A {
                    func reset(): Unit {
                        this.x = 2
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }
}
