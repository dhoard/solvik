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
 * Negative semantic tests for the universal {@code Any.equals(other: Any?): Boolean} member
 * (docs/LANGUAGE_SPEC.md section 3). A user override must be exactly one parameter typed
 * {@code Any?} and return exactly {@code Boolean}, must be declared {@code override}, and cannot be
 * restated by an interface or stored in a property or delegate. The failures reuse the ordinary
 * override and reserved-member diagnostics and are reported during declaration collection.
 */
public final class SolvikEqualsOverrideNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("equalsneg.sol", text);
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
    public void missingOverrideIsRejected() {
        String text = """
                class Point {
                    func equals(other: Any?): Boolean {
                        return true
                    }
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.SEM_ACCIDENTAL_OVERRIDE);
    }

    @Test
    public void wrongArityIsRejected() {
        String text = """
                class Point {
                    override func equals(other: Any?, extra: Any?): Boolean {
                        return true
                    }
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.SEM_OVERRIDE_SIGNATURE);
    }

    @Test
    public void parameterTypeOtherThanAnyNullableIsRejected() {
        String text = """
                class Point {
                    override func equals(other: Integer): Boolean {
                        return true
                    }
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.SEM_OVERRIDE_SIGNATURE);
    }

    @Test
    public void returnTypeOtherThanBooleanIsRejected() {
        String text = """
                class Point {
                    override func equals(other: Any?): Integer {
                        return 1
                    }
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.SEM_OVERRIDE_SIGNATURE);
    }

    @Test
    public void interfaceCannotDeclareEquals() {
        String text = """
                interface Named {
                    func equals(other: Any?): Boolean
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.SEM_RESERVED_MEMBER);
    }

    @Test
    public void interfaceCannotDeclareDefaultEquals() {
        String text = """
                interface Named {
                    func equals(other: Any?): Boolean {
                        return true
                    }
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.SEM_RESERVED_MEMBER);
    }

    @Test
    public void propertyCannotBeNamedEquals() {
        String text = """
                class Point {
                    val equals: Integer

                    Point() {
                        this.equals = 1
                    }
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.SEM_RESERVED_MEMBER);
    }

    @Test
    public void aFinalOverrideCannotBeOverriddenAgain() {
        // Both classes pair equals with hashCode so the equals/hashCode pairing rule does not fire and
        // the diagnostic under test is the final-override violation alone.
        String text = """
                open class Base {
                    override func equals(other: Any?): Boolean {
                        return true
                    }

                    override func hashCode(): Integer {
                        return 1
                    }
                }

                class Derived extends Base {
                    override func equals(other: Any?): Boolean {
                        return false
                    }

                    override func hashCode(): Integer {
                        return 2
                    }
                }
                """;
        assertThat(first(checkFails(text)).code()).isEqualTo(DiagnosticCode.SEM_OVERRIDE_FINAL);
    }

    @Test
    public void bareEqualsMemberReadIsRejected() {
        String text = """
                class Point {
                }

                func f(): Boolean {
                    val p = Point()
                    val read = p.equals
                    return true
                }
                """;
        assertThat(checkFails(text).all()).isNotEmpty();
    }

    @Test
    public void delegateCannotBeNamedEquals() {
        String text = """
                interface Greeter {
                    func greet(): String
                }

                class Service implements Greeter {
                    delegate val equals: Greeter

                    Service(greeter: Greeter) {
                        this.equals = greeter
                    }
                }
                """;
        DiagnosticBag bag = checkFails(text);
        assertThat(bag.all()).anySatisfy(d -> assertThat(d.code()).isEqualTo(DiagnosticCode.SEM_RESERVED_MEMBER));
    }
}
