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
 * Negative Phase 8 semantic tests: a missing interface implementation, conflicting interface defaults
 * that the class does not resolve, an implementation whose signature does not conform, an invalid
 * interface reference, an interface-extension cycle, and an interface used as a value each produce a
 * source-located diagnostic and no typed result.
 */
public final class SolvikInterfaceNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("interfaceneg.sol", text);
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
    public void missingInterfaceImplementationIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                class User implements Named {
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_MISSING_INTERFACE_IMPLEMENTATION);
    }

    @Test
    public void missingInheritedRequirementImplementationIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                interface Aged extends Named {
                    func age(): Int
                }
                class User implements Aged {
                    func age(): Int {
                        return 1
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_MISSING_INTERFACE_IMPLEMENTATION);
    }

    @Test
    public void oneRequirementPerClassIsReportedEvenWithDefaultsElsewhere() {
        DiagnosticBag bag = checkFails("""
                interface Named {
                    func name(): String

                    func greeting(): String {
                        return "Hello " .. name()
                    }
                }
                interface Aged {
                    func age(): Int
                }
                class User implements Named, Aged {
                }
                """);
        // `name` (no default) and `age` are both missing; `greeting` is not reported.
        List<DiagnosticCode> codes = bag.all().stream().map(Diagnostic::code).toList();
        assertThat(codes.contains(DiagnosticCode.SEM_MISSING_INTERFACE_IMPLEMENTATION)).as(codes.toString()).isTrue();
        assertThat(codes.stream().filter(c -> c == DiagnosticCode.SEM_MISSING_INTERFACE_IMPLEMENTATION).count()).isEqualTo(2);
    }

    @Test
    public void conflictingDefaultsRequireExplicitResolution() {
        Diagnostic diagnostic = first(checkFails("""
                interface A {
                    func greet(): String {
                        return "a"
                    }
                }
                interface B {
                    func greet(): String {
                        return "b"
                    }
                }
                class C implements A, B {
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_CONFLICTING_DEFAULTS);
    }

    @Test
    public void conflictingDefaultsAreResolvedByAnExplicitMethod() {
        CompilationUnitNode unit = parseOk("resolve.sol", """
                interface A {
                    func greet(): String {
                        return "a"
                    }
                }
                interface B {
                    func greet(): String {
                        return "b"
                    }
                }
                class C implements A, B {
                    func greet(): String {
                        return "c"
                    }
                }
                """);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("explicit resolution must compile: " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void conflictingDefaultsAreResolvedByAnInheritedMethod() {
        CompilationUnitNode unit = parseOk("resolveinherited.sol", """
                interface A {
                    func greet(): String {
                        return "a"
                    }
                }
                interface B {
                    func greet(): String {
                        return "b"
                    }
                }
                open class Base {
                    func greet(): String {
                        return "base"
                    }
                }
                class C extends Base implements A, B {
                }
                """);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("inherited resolution must compile: " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void conflictingDefaultsFromExtendedInterfacesAreRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface A {
                    func greet(): String {
                        return "a"
                    }
                }
                interface B {
                    func greet(): String {
                        return "b"
                    }
                }
                interface Both extends A, B {
                }
                class C implements Both {
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_CONFLICTING_DEFAULTS);
    }

    @Test
    public void implementationWithWrongParameterTypesIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Filter {
                    func accepts(value: Int): Boolean
                }
                class Odd implements Filter {
                    func accepts(value: String): Boolean {
                        return true
                    }
                }
                """));
        // The parameter mismatch is reported first; the return type is coincidentally fine.
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_IMPLEMENTATION_SIGNATURE);
    }

    @Test
    public void implementationWithNonCovariantReturnTypeIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                class User implements Named {
                    func name(): Int {
                        return 1
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_IMPLEMENTATION_SIGNATURE);
    }

    @Test
    public void methodThatDoesNotConformToAnInheritedDefaultIsRejected() {
        // `label` is only supplied by Base's default, but C's method must still be usable through
        // Base: a call through Base-typed code would otherwise bind C's incompatible method.
        Diagnostic diagnostic = first(checkFails("""
                interface Base {
                    func label(): String {
                        return "base"
                    }
                }
                class C implements Base {
                    func label(): Int {
                        return 1
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_IMPLEMENTATION_SIGNATURE);
    }

    @Test
    public void implementsOnANonInterfaceIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class Base {
                }
                class Derived implements Base {
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_INVALID_INTERFACE);
    }

    @Test
    public void implementsOnABuiltinTypeIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C implements String {
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_INVALID_INTERFACE);
    }

    @Test
    public void interfaceExtendingAClassIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class Base {
                }
                interface I extends Base {
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_INVALID_INTERFACE);
    }

    @Test
    public void unknownInterfaceNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C implements Missing {
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }

    @Test
    public void interfaceExtensionCycleIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface A extends B {
                }
                interface B extends A {
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_INTERFACE_CYCLE);
    }

    @Test
    public void selfExtendingInterfaceCycleIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface A extends A {
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_INTERFACE_CYCLE);
    }

    @Test
    public void duplicateInterfaceNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface I {
                    func f(): Int
                }
                interface I {
                    func g(): Int
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void duplicateInterfaceMemberIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface I {
                    func f(): Int

                    func f(): String
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void classAndInterfaceShareOneTypeNameSpace() {
        Diagnostic diagnostic = first(checkFails("""
                class Thing {
                }

                interface Thing {
                    func f(): Int
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void interfaceNameCannotBeUsedAsAValue() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                func use(): Int {
                    val x: Int = Named()
                    return x
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_INTERFACE_AS_VALUE);
    }

    @Test
    public void interfaceMemberCannotBeReadAsAValue() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                func use(named: Named): String {
                    return named.name
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_FUNCTION_AS_VALUE);
    }

    @Test
    public void unknownMemberThroughAnInterfaceReceiverIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                func use(named: Named): String {
                    return named.label()
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void wrongArgumentTypeThroughAnInterfaceReceiverIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Greeter {
                    func greet(value: Int): String
                }
                func use(greeter: Greeter): String {
                    return greeter.greet("x")
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void callWithWrongArityThroughAnInterfaceReceiverIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Greeter {
                    func greet(value: Int): String
                }
                func use(greeter: Greeter): String {
                    return greeter.greet()
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void anInterfaceDoesNotLeakMembersIntoALaterTopLevelFunctionBody() {
        // Bodies are checked in source order, so the interface is checked first; its member context
        // must not remain visible to a later top-level function.
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                class User implements Named {
                    func name(): String {
                        return "Doug"
                    }
                }
                func later(): String {
                    return name()
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void interfaceValueCannotBeAssignedToAnImplementingClassType() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                class User implements Named {
                    func name(): String {
                        return "Doug"
                    }
                }
                func use(named: Named): User {
                    return named
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_RETURN_MISMATCH);
    }

    @Test
    public void thisIsRejectedInsideAnInterfaceDefaultThatIsNotAMember() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                func helper(): Int {
                    return this.hashCode()
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_THIS_OUTSIDE_CLASS);
    }

    @Test
    public void restatingAnExtendedDefaultAsARequirementIsRejected() {
        // The inherited default stays reachable through the extension, so the restated signature
        // would leave a conforming class with both a requirement and a default for one name.
        Diagnostic diagnostic = first(checkFails("""
                interface Base {
                    func label(): String {
                        return "base"
                    }
                }
                interface Derived extends Base {
                    func label(): String
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_INVALID_INTERFACE);
    }

    @Test
    public void classExtendingAnInterfaceIsRejected() {
        // `extends` is class inheritance; a nominal contract is joined with `implements`.
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                class User extends Named {
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_INVALID_SUPERCLASS);
    }

    @Test
    public void superIsRejectedInsideAnInterfaceDefaultBody() {
        // An interface has no superclass implementation to reach.
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String {
                        return super.name()
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_SUPER_OUTSIDE_CLASS);
    }

    @Test
    public void overrideOnAnInterfaceImplementationIsRejected() {
        // `override` governs class inheritance only; an implementing method carries no modifier.
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                class User implements Named {
                    override func name(): String {
                        return "Doug"
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_OVERRIDE_WITHOUT_SUPER);
    }

    @Test
    public void implementingMethodWithArityMismatchIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(prefix: String): String
                }
                class User implements Named {
                    func name(): String {
                        return "Doug"
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_IMPLEMENTATION_SIGNATURE);
    }
}
