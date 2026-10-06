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
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.FunctionTypeRefNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * First-class function TYPE tests: a function type reference parses, resolves, and is accepted in
 * every declared-type position that does not itself require a function value — parameters, results,
 * nested function types, generic arguments, and property types — and its written spellings decide
 * interface implementation matching. Function-producing expressions are still rejected until the
 * phase that gives a declaration a value, and function types are rejected as reified
 * {@code is}/{@code as}/superclass targets (docs/LANGUAGE_SPEC.md sections 6 and 7).
 */
public final class SolvikFunctionTypeTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("functype.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
        return result.requireProgram();
    }

    private static DiagnosticCode firstCode(String text) {
        CompilationUnitNode unit = parseOk("functype.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must fail: " + text).isFalse();
        DiagnosticBag bag = result.diagnostics();
        assertThat(bag.hasErrors()).isTrue();
        List<Diagnostic> all = bag.all();
        assertThat(all.isEmpty()).isFalse();
        return all.get(0).code();
    }

    /** A function type written as a parameter type parses to a FunctionTypeRefNode. */
    @Test
    public void functionTypeParsesAsParameterType() {
        CompilationUnitNode unit = parseOk("functype.sol", "func take(cb: func(Integer): Unit): Unit {\n}\n");
        FunctionDeclNode take = (FunctionDeclNode) unit.declarations().get(0);
        assertThat(take.parameters().get(0).type()).isInstanceOf(FunctionTypeRefNode.class);
    }

    /** A function type in a parameter position resolves without error. */
    @Test
    public void functionTypeInParameterPositionResolves() {
        check("func take(cb: func(Integer): Unit): Unit {\n}\n");
    }

    /** A nested function type (a function type in the result of another) resolves. */
    @Test
    public void nestedFunctionTypeResolves() {
        check("func outer(cb: func(): func(Integer): Unit): Unit {\n}\n");
    }

    /** The grouped nullable function form `(func(...): R)?` resolves in a parameter position. */
    @Test
    public void nullableFunctionTypeResolves() {
        check("func maybe(cb: (func(Integer): Unit)?): Unit {\n}\n");
    }

    /** An omitted function return type is `Unit`, so `func()` is accepted as a parameter type. */
    @Test
    public void omittedFunctionReturnTypeResolves() {
        check("func run(cb: func()): Unit {\n}\n");
    }

    /** A function type may be an interface signature result type (no body, so no value needed). */
    @Test
    public void functionTypeAsSignatureResultResolves() {
        check("""
                interface Provider {
                    func get(): func(Integer): Unit
                }
                """);
    }

    /** A function type may appear as a generic type argument (docs/LANGUAGE_SPEC.md section 4.1). */
    @Test
    public void functionTypeAsGenericArgumentResolves() {
        check("""
                func use(): Unit {
                    var callbacks: List<func(String): Unit> = List()
                }
                """);
    }

    /**
     * A bare reference to a visible top-level function is a function value, and its inferred type is the
     * declaration's function type (docs/LANGUAGE_SPEC.md section 6). This replaces the Phase 1 rejection,
     * which the revision removed rather than relaxed.
     */
    @Test
    public void namedFunctionReferenceInfersItsFunctionType() {
        check("""
                func format(value: Integer): String {
                    return value.toString()
                }
                func use(): Unit {
                    var f = format
                }
                """);
    }

    /**
     * A generic function used as a value needs an expected function type to instantiate it.
     *
     * <p>"A generic function reference with no expected function type is `SOLV-TYPE-030`" (section 6).
     * Instantiation under a declared type is covered by {@code SolvikGenericFunctionValueTest}.
     */
    @Test
    public void genericFunctionReferenceNeedsAnExpectedFunctionType() {
        assertThat(firstCode("""
                func identity<T>(value: T): T {
                    return value
                }
                func use(): Unit {
                    var f = identity
                }
                """)).isEqualTo(DiagnosticCode.TYPE_CANNOT_INFER);
    }

    /** A function type cannot be a superclass (only classes and Any are). */
    @Test
    public void functionTypeAsSuperclassIsRejected() {
        assertThat(firstCode("class Broken extends func(): Unit {\n}\n")).isEqualTo(DiagnosticCode.SEM_INVALID_SUPERCLASS);
    }

    /** A function type cannot be the target of a type test (it is not reifiable). */
    @Test
    public void functionTypeIsRejectedAsTypeTestTarget() {
        assertThat(firstCode("""
                func use(x: Any): Boolean {
                    return x is func(): Unit
                }
                """)).isEqualTo(DiagnosticCode.TYPE_INVALID_TYPE_OPERAND);
    }

    /** A function type cannot be the target of a checked cast (it is not reifiable). */
    @Test
    public void functionTypeIsRejectedAsCastTarget() {
        assertThat(firstCode("""
                func use(x: Any): Unit {
                    var y = x as func(): Unit
                }
                """)).isEqualTo(DiagnosticCode.TYPE_INVALID_TYPE_OPERAND);
    }

    /**
     * A function type may be the declared type of a static property, in the grouped nullable
     * spelling and the nullable-result spelling, and needs no initializer because a reference type
     * starts at {@code null} (docs/LANGUAGE_SPEC.md sections 6 and 7).
     */
    @Test
    public void functionTypeAsStaticPropertyTypeResolves() {
        check("""
                class Holder {
                    static var operation: (func(Integer): Unit)? = null
                    static var sharedOperation: (func(Integer): Unit)?
                    static var nullableResult: func(Integer): Unit?
                }
                """);
    }

    /** {@code null} is not assignable to a non-null function type. */
    @Test
    public void nullIsNotAssignableToANonNullFunctionType() {
        assertThat(firstCode("""
                class Holder {
                    static var operation: func(Integer): Unit = null
                }
                """)).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    /**
     * A function type written with the {@code Unit} return spelled out and one written with it
     * omitted are the same type, so an implementation may use either spelling — and may name its own
     * parameter differently — and still satisfy the interface signature (docs/LANGUAGE_SPEC.md
     * section 6: an omitted return type means {@code Unit}, so {@code func()} and
     * {@code func(): Unit} name the same type).
     */
    @Test
    public void functionTypeSpellingsMatchAnInterfaceSignature() {
        check("""
                interface Scheduler {
                    func run(basic: func(): Unit): Unit
                }
                class Job implements Scheduler {
                    func run(ignored: func()) {
                    }
                }
                """);
    }

    /**
     * The types written <em>inside</em> a function type participate in implementation matching, so
     * an implementation differing only in a nested function type does not satisfy the interface
     * (section 6 defines identity structurally, section 12 requires the same parameter types).
     */
    @Test
    public void implementationDifferingInsideANestedFunctionTypeIsRejected() {
        assertThat(firstCode("""
                interface Factory {
                    func use(builder: func(): func(Integer): Unit): Unit
                }
                class Simple implements Factory {
                    func use(builder: func(): func(): Unit) {
                    }
                }
                """)).isEqualTo(DiagnosticCode.SEM_IMPLEMENTATION_SIGNATURE);
    }

    /**
     * Nullability of the function value and nullability of its result are different types, so an
     * implementation written with the nullable-result spelling does not satisfy a parameter written
     * as a nullable function value (section 6: the grouping is part of the written type).
     */
    @Test
    public void groupedNullableFunctionTypeIsNotNullableResultFunctionType() {
        assertThat(firstCode("""
                interface Holder {
                    func take(operation: (func(Integer): Unit)?): Unit
                }
                class Simple implements Holder {
                    func take(operation: func(Integer): Unit?) {
                    }
                }
                """)).isEqualTo(DiagnosticCode.SEM_IMPLEMENTATION_SIGNATURE);
    }

    /** An unknown type inside a nested function type in a return type is reported at its written reference. */
    @Test
    public void unknownTypeInsideNestedFunctionTypeIsReportedAtTheReference() {
        assertThat(firstCode("""
                class Holder {
                    func make(): func(Integer): func(Widget): Unit {
                    }
                }
                """)).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }
}
