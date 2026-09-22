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
 * Negative Phase 6 static-semantics tests: undeclared members, property mutability, constructor
 * definite initialization, method/constructor argument checking, and class-specific name
 * resolution each produce a source-located diagnostic and no typed result.
 */
public final class SolvikClassSemanticNegativeTest {

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("classneg.sol", text);
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
    public void undeclaredPropertyReadIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Integer = 1
                }
                func f(): Integer {
                    return C().y
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void undeclaredPropertyWriteIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Integer = 1
                }
                func f(): Unit {
                    C().y = 2
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void memberAccessOnABuiltinIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(s: String): Unit {\n    val x = s.length\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void writeToValPropertyAfterConstructionIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Integer
                    C() {
                        this.x = 1
                    }
                    func reset(): Unit {
                        this.x = 2
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }

    @Test
    public void writeToValPropertyWithInitializerInConstructorIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Integer = 1
                    C() {
                        this.x = 2
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }

    @Test
    public void doubleAssignmentToValPropertyInConstructorIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Integer
                    C() {
                        this.x = 1
                        this.x = 2
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }

    @Test
    public void readOfPropertyBeforeInitializationIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Integer
                    C() {
                        val before = this.x
                        this.x = 1
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_UNINITIALIZED_PROPERTY);
    }

    @Test
    public void propertyMissingOnOneConstructorPathIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Integer
                    C() {
                        if (true) {
                            this.x = 1
                        }
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISSING_PROPERTY_INITIALIZER);
    }

    @Test
    public void earlyReturnWithoutInitializationIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Integer
                    C() {
                        if (true) {
                            return
                        }
                        this.x = 1
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISSING_PROPERTY_INITIALIZER);
    }

    @Test
    public void classWithoutConstructorNeedsEveryPropertyInitialized() {
        Diagnostic diagnostic = first(checkFails("class C {\n    val x: Integer\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_CLASS_REQUIRES_INITIALIZER);
    }

    @Test
    public void wrongConstructorArgumentTypeIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Integer
                    C(x: Integer) {
                        this.x = x
                    }
                }
                func f(): C {
                    return C("s")
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void constructorArityMustMatch() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Integer
                    C(x: Integer) {
                        this.x = x
                    }
                }
                func f(): C {
                    return C()
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void wrongMethodArgumentTypeIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    func set(x: Integer): Unit {
                    }
                }
                func f(): Unit {
                    C().set("s")
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void unknownMethodIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Integer = 1
                }
                func f(): Unit {
                    C().missing()
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void callingAPropertyIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Integer = 1
                }
                func f(): Unit {
                    C().x()
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_NOT_CALLABLE);
    }

    @Test
    public void methodUsedAsAValueIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    func f(): Integer {
                        return 1
                    }
                }
                func g(): Integer {
                    val h = C().f
                    return 1
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_FUNCTION_AS_VALUE);
    }

    @Test
    public void assigningToAMethodIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    func f(): Unit {
                    }
                }
                func g(): Unit {
                    C().f = 1
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_INVALID_ASSIGNMENT_TARGET);
    }

    @Test
    public void aBarePropertyReadInsideAMethodIsAnUnknownName() {
        // docs/LANGUAGE_SPEC.md section 7: a property is reached only through `this`, so a bare property
        // name is not a member reference at all and resolves as an unknown name rather than silently
        // binding to the property.
        Diagnostic diagnostic = first(checkFails("""
                class Box {
                    val n: Integer

                    Box() {
                        this.n = 7
                    }

                    func get(): Integer {
                        return n
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void aBarePropertyWriteInsideAConstructorIsAnUnknownName() {
        // The constructor must also qualify the property with `this`; otherwise `n = 7` below would
        // assign a fresh local and leave the property uninitialized.
        DiagnosticBag bag = checkFails("""
                class Box {
                    val n: Integer

                    Box() {
                        n = 7
                    }
                }
                """);
        assertThat(bag.all().stream().map(Diagnostic::code).toList())
                .containsExactly(DiagnosticCode.RESOL_UNKNOWN_NAME, DiagnosticCode.TYPE_MISSING_PROPERTY_INITIALIZER);
    }

    @Test
    public void aBareInheritedPropertyReadIsAnUnknownName() {
        Diagnostic diagnostic = first(checkFails("""
                open class Base {
                    val id: Integer = 5
                }

                class Derived extends Base {
                    func show(): Integer {
                        return id
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_NAME);
    }

    @Test
    public void thisOutsideAClassIsRejected() {
        Diagnostic diagnostic = first(checkFails("func f(): Unit {\n    val x = this\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_THIS_OUTSIDE_CLASS);
    }

    @Test
    public void classNamesAreNotValues() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Integer = 1
                }
                func f(): C {
                    val c = C
                    return c
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_CLASS_AS_VALUE);
    }

    @Test
    public void duplicatePropertyIsRejected() {
        Diagnostic diagnostic = first(checkFails("class C {\n    val x: Integer = 1\n    val x: Integer = 2\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void duplicateMethodIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    func f(): Unit {
                    }
                    func f(): Unit {
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void propertyAndMethodMayNotShareAName() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Integer = 1
                    func x(): Integer {
                        return 1
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void moreThanOneConstructorIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    C() {
                    }
                    C(x: Integer) {
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_DUPLICATE_CONSTRUCTOR);
    }

    @Test
    public void duplicateClassNameIsRejected() {
        Diagnostic diagnostic = first(checkFails("class C {\n}\nclass C {\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void classMayNotShadowABuiltinTypeName() {
        Diagnostic diagnostic = first(checkFails("class String {\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void propertyInitializerTypeMustMatch() {
        Diagnostic diagnostic = first(checkFails("class C {\n    val x: Integer = \"s\"\n}\n"));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void constructorMayNotReturnAValue() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val x: Integer = 1
                    C() {
                        return 1
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_UNEXPECTED_RETURN_VALUE);
    }

    @Test
    public void constructorNameMustMatchItsClass() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    D() {
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_CONSTRUCTOR_NAME);
    }

    @Test
    public void methodNamedAfterItsClassIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    func C(): Integer {
                        return 1
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_MEMBER_NAMED_AFTER_CLASS);
    }

    @Test
    public void propertyNamedAfterItsClassIsRejected() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    val C: Integer = 1
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.SEM_MEMBER_NAMED_AFTER_CLASS);
    }

    @Test
    public void valueReturningMethodNeedsReturnOnEveryPath() {
        Diagnostic diagnostic = first(checkFails("""
                class C {
                    func f(): Integer {
                        if (true) {
                            return 1
                        }
                    }
                }
                """));
        assertThat(diagnostic.code()).isEqualTo(DiagnosticCode.TYPE_MISSING_RETURN_PATH);
    }
}
