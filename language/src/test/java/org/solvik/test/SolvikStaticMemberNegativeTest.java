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
 * Negative static-semantics tests for static members and the class initializer
 * (docs/LANGUAGE_SPEC.md section 7): the one-block-per-class rule, modifiers a static member cannot
 * carry, name collisions with the instance namespace, the absence of a receiver inside a static
 * member, and the exclusion of a class's own type parameters from its static members.
 */
public final class SolvikStaticMemberNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("staticneg.sol", text);
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

    /**
     * Asserts the single offense produced by {@code text}. Every case below is written so exactly one
     * diagnostic is reported, which is what keeps an unrelated rule from masking the one under test.
     */
    private Diagnostic only(DiagnosticBag bag) {
        assertThat(bag.all()).hasSize(1);
        return bag.all().get(0);
    }

    @Test
    public void aSecondStaticInitializerBlockIsRejected() {
        String text = """
                class Counter {
                    static {
                        println("first")
                    }
                    static {
                        println("second")
                    }
                }
                """;
        Diagnostic diagnostic = only(checkFails(text));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_DUPLICATE_STATIC_BLOCK);
        // The diagnostic names the second block rather than the first, which is the offending one.
        int secondBlock = text.indexOf("second");
        assertThat(text.lastIndexOf("static", secondBlock)).isEqualTo(diagnostic.span().startOffset());
    }

    @Test
    public void anOpenStaticMethodIsRejected() {
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    static open func reset() {
                        println("reset")
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_INVALID_STATIC_MODIFIER);
        assertThat(diagnostic.message()).contains("reset");
    }

    @Test
    public void anOverrideStaticMethodIsRejected() {
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    static override func reset() {
                        println("reset")
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_INVALID_STATIC_MODIFIER);
    }

    @Test
    public void aStaticPropertyMayNotShareItsNameWithAnInstanceProperty() {
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    val count: Integer = 1
                    static val count: Integer = 2
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
        assertThat(diagnostic.message()).isEqualTo("property 'count' is already declared");
    }

    @Test
    public void aStaticPropertyMayNotBeDeclaredTwice() {
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    static val limit: Integer = 1
                    static val limit: Integer = 2
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
        assertThat(diagnostic.message()).isEqualTo("property 'limit' is already declared");
    }

    @Test
    public void aStaticMethodMayNotShareItsNameWithAnInstanceProperty() {
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    val count: Integer = 1
                    static func count(): Integer {
                        return 1
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
        assertThat(diagnostic.message()).isEqualTo("member 'count' is already declared");
    }

    @Test
    public void aStaticMethodMayNotShareItsNameWithAnInstanceMethod() {
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    func reset() {
                        println("instance")
                    }
                    static func reset() {
                        println("static")
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
        assertThat(diagnostic.message()).isEqualTo("member 'reset' is already declared");
    }

    @Test
    public void aStaticMethodMayNotBeDeclaredTwice() {
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    static func reset() {
                        println("first")
                    }
                    static func reset() {
                        println("second")
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
        assertThat(diagnostic.message()).isEqualTo("member 'reset' is already declared");
    }

    @Test
    public void aStaticPropertyMayNotShareItsNameWithAStaticMethodDeclaredFirst() {
        // The collision is between two static members, caught by the static method pass comparing
        // against the static property names it collected immediately before.
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    static func reset(): Integer {
                        return 1
                    }
                    static val reset: Integer = 2
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void aStaticPropertyMayNotShareItsNameWithAnInstanceMethodDeclaredFirst() {
        // Regression: the static property pass compared its name only against the instance property
        // names, so a static property beside an instance method of the same name was accepted even
        // though the two share one member namespace. `Counter.reset` would then name the static cell
        // while a call site resolved the instance method, with nothing reported for either
        // (docs/LANGUAGE_SPEC.md section 7). Written in the other order, and with an instance rather
        // than a static method: source order must not decide whether the collision is caught.
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    func reset(): Integer {
                        return 1
                    }
                    static val reset: Integer = 2
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void aStaticPropertyMayNotShareItsNameWithAnInstanceMethodDeclaredAfterIt() {
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    static val reset: Integer = 2
                    func reset(): Integer {
                        return 1
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void thisIsNotAvailableInsideAStaticMethodBody() {
        // The enclosing class is still recorded while a static body is checked so its type parameters
        // resolve; `this` must therefore be refused explicitly rather than typed from that record.
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    static func reset() {
                        println(this)
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_THIS_OUTSIDE_CLASS);
        assertThat(diagnostic.message()).contains("static member");
    }

    @Test
    public void thisIsNotAvailableInsideAClassInitializerBlock() {
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    static {
                        println(this)
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_THIS_OUTSIDE_CLASS);
        assertThat(diagnostic.message()).contains("class initializer block");
    }

    @Test
    public void superIsNotAvailableInsideAStaticMethodBody() {
        Diagnostic diagnostic = first(checkFails("""
                open class Base {
                }
                class Counter extends Base {
                    static func reset() {
                        super()
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_SUPER_OUTSIDE_CLASS);
        assertThat(diagnostic.message()).contains("static member");
    }

    @Test
    public void anUnqualifiedCallInsideAStaticMemberCannotReachAnInstanceMethod() {
        // This is the soundness case: resolving `f()` through the virtual table would bind a method
        // whose implicit receiver does not exist, because the enclosing static has none.
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    func count(): Integer {
                        return 1
                    }
                    static func total(): Integer {
                        return count()
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
        assertThat(diagnostic.message()).contains("no receiver").contains("instance method 'count'");
    }

    @Test
    public void anUnqualifiedCallInsideAClassInitializerCannotReachAnInstanceMethod() {
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    func count(): Integer {
                        return 1
                    }
                    static {
                        val ignored: Integer = count()
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
        assertThat(diagnostic.message()).contains("no receiver").contains("instance method 'count'");
    }

    @Test
    public void anUnknownNameInsideAStaticMemberReportsTheOrdinaryUnknownNameDiagnostic() {
        // Only names that really are instance members get the receiver explanation; a name that exists
        // nowhere must not claim otherwise.
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    static func total(): Integer {
                        return nothing()
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
        assertThat(diagnostic.message()).isEqualTo("unknown name 'nothing'");
    }

    @Test
    public void aStaticPropertyMayNotUseATypeParameterOfItsClass() {
        Diagnostic diagnostic = only(checkFails("""
                class Box<T> {
                    val value: T
                    static val fallback: T
                    Box(value: T) {
                        this.value = value
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_TYPE_PARAMETER_IN_STATIC_MEMBER);
        assertThat(diagnostic.message()).contains("'T'");
    }

    @Test
    public void aStaticMethodSignatureMayNotUseATypeParameterOfItsClass() {
        // Both the parameter and the return type reference `T`, so the receiver reports the first
        // offense and the test asserts the rule rather than an exhaustive count.
        Diagnostic diagnostic = first(checkFails("""
                class Box<T> {
                    val value: T
                    static func pick(input: T): T {
                        return input
                    }
                    Box(value: T) {
                        this.value = value
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_TYPE_PARAMETER_IN_STATIC_MEMBER);
    }

    @Test
    public void aStaticMethodBodyMayNotUseATypeParameterOfItsClass() {
        Diagnostic diagnostic = first(checkFails("""
                class Box<T> {
                    val value: T
                    static func bad(): Integer {
                        val converted: Integer = (1 as T)
                        return converted
                    }
                    Box(value: T) {
                        this.value = value
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_TYPE_PARAMETER_IN_STATIC_MEMBER);
    }

    @Test
    public void aStaticMemberNamedAfterItsClassIsStillRejected() {
        // The rule that forbids a member sharing its class's name governs static members too, and the
        // member span begins at `static`, so the diagnostic points at the whole declaration.
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    static val Counter: Integer = 1
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_MEMBER_NAMED_AFTER_CLASS);
    }

    @Test
    public void aStaticInitializerThatDoesNotMatchItsDeclaredTypeIsRejected() {
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    static val limit: Integer = "twelve"
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
        // The message must name a static property, not misattribute the error to instance layout.
        assertThat(diagnostic.message()).contains("static property type Integer");
    }

    @Test
    public void aStaticInitializerIsCheckedExactlyOnce() {
        // One broken reference must produce exactly one diagnostic: the instance-initializer pass and
        // the static pass must not both visit the same expression.
        Diagnostic diagnostic = only(checkFails("""
                class Counter {
                    static val limit: Integer = missingValue
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }
}
