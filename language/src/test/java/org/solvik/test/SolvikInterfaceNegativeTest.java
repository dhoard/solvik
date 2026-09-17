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
 * Negative Phase 8 semantic tests: a missing interface implementation, conflicting interface defaults
 * that the class does not resolve, an implementation whose signature does not conform, an invalid
 * interface reference, an interface-extension cycle, and an interface used as a value each produce a
 * source-located diagnostic and no typed result.
 */
public final class SolvikInterfaceNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("interfaceneg.sol", text);
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
    public void missingInterfaceImplementationIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface Named {
                    func name(): String
                }
                class User implements Named {
                }
                """));
        assertEquals(DiagnosticCode.SEM_MISSING_INTERFACE_IMPLEMENTATION, diagnostic.code());
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
        assertEquals(DiagnosticCode.SEM_MISSING_INTERFACE_IMPLEMENTATION, diagnostic.code());
    }

    @Test
    public void oneRequirementPerClassIsReportedEvenWithDefaultsElsewhere() {
        DiagnosticBag bag = checkFails("""
                interface Named {
                    func name(): String

                    func greeting(): String {
                        return "Hello " + name()
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
        assertTrue(codes.toString(), codes.contains(DiagnosticCode.SEM_MISSING_INTERFACE_IMPLEMENTATION));
        assertEquals(2, codes.stream().filter(c -> c == DiagnosticCode.SEM_MISSING_INTERFACE_IMPLEMENTATION).count());
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
        assertEquals(DiagnosticCode.SEM_CONFLICTING_DEFAULTS, diagnostic.code());
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
        assertTrue("explicit resolution must compile: " + result.diagnostics().all(), result.isSuccess());
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
        assertTrue("inherited resolution must compile: " + result.diagnostics().all(), result.isSuccess());
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
        assertEquals(DiagnosticCode.SEM_CONFLICTING_DEFAULTS, diagnostic.code());
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
        assertEquals(DiagnosticCode.SEM_IMPLEMENTATION_SIGNATURE, diagnostic.code());
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
        assertEquals(DiagnosticCode.SEM_IMPLEMENTATION_SIGNATURE, diagnostic.code());
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
        assertEquals(DiagnosticCode.SEM_IMPLEMENTATION_SIGNATURE, diagnostic.code());
    }

    @Test
    public void implementsOnANonInterfaceIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class Base {
                }
                class Derived implements Base {
                }
                """));
        assertEquals(DiagnosticCode.SEM_INVALID_INTERFACE, diagnostic.code());
    }

    @Test
    public void implementsOnABuiltinTypeIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C implements String {
                }
                """));
        assertEquals(DiagnosticCode.SEM_INVALID_INTERFACE, diagnostic.code());
    }

    @Test
    public void interfaceExtendingAClassIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class Base {
                }
                interface I extends Base {
                }
                """));
        assertEquals(DiagnosticCode.SEM_INVALID_INTERFACE, diagnostic.code());
    }

    @Test
    public void unknownInterfaceNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C implements Missing {
                }
                """));
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_TYPE, diagnostic.code());
    }

    @Test
    public void interfaceExtensionCycleIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface A extends B {
                }
                interface B extends A {
                }
                """));
        assertEquals(DiagnosticCode.SEM_INTERFACE_CYCLE, diagnostic.code());
    }

    @Test
    public void selfExtendingInterfaceCycleIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface A extends A {
                }
                """));
        assertEquals(DiagnosticCode.SEM_INTERFACE_CYCLE, diagnostic.code());
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
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
    }

    @Test
    public void duplicateInterfaceMemberIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                interface I {
                    func f(): Int

                    func f(): String
                }
                """));
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
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
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, diagnostic.code());
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
        assertEquals(DiagnosticCode.TYPE_INTERFACE_AS_VALUE, diagnostic.code());
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
        assertEquals(DiagnosticCode.TYPE_FUNCTION_AS_VALUE, diagnostic.code());
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
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_MEMBER, diagnostic.code());
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
        assertEquals(DiagnosticCode.TYPE_MISMATCH, diagnostic.code());
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
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, diagnostic.code());
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
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_NAME, diagnostic.code());
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
        assertEquals(DiagnosticCode.TYPE_RETURN_MISMATCH, diagnostic.code());
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
        assertEquals(DiagnosticCode.RESOL_THIS_OUTSIDE_CLASS, diagnostic.code());
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
        assertEquals(DiagnosticCode.SEM_INVALID_INTERFACE, diagnostic.code());
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
        assertEquals(DiagnosticCode.SEM_INVALID_SUPERCLASS, diagnostic.code());
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
        assertEquals(DiagnosticCode.RESOL_SUPER_OUTSIDE_CLASS, diagnostic.code());
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
        assertEquals(DiagnosticCode.SEM_OVERRIDE_WITHOUT_SUPER, diagnostic.code());
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
        assertEquals(DiagnosticCode.SEM_IMPLEMENTATION_SIGNATURE, diagnostic.code());
    }
}
