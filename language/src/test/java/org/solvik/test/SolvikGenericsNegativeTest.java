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
 * Negative Phase 11 semantic tests (docs/LANGUAGE_SPEC.md section 11): raw generic types, type
 * argument arity and application errors, invariance violations, unbound inference, erased type tests
 * and casts, and generic interface conformance failures.
 */
public final class SolvikGenericsNegativeTest {

    private static final String BOX = """
            class Box<T> {
                var mutable value: T

                Box(value: T) {
                    this.value = value
                }
            }
            """;

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("gneg.sol", text);
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
    public void rawGenericTypeIsRejected() {
        assertThat(first(checkFails(BOX + """
                func f() {
                    var box: Box = Box(5)
                }

                """)).code()).isEqualTo(DiagnosticCode.TYPE_RAW_GENERIC_TYPE);
    }

    @Test
    public void rawBuiltinListIsRejected() {
        assertThat(first(checkFails("""
                func f(values: List) {
                }

                """)).code()).isEqualTo(DiagnosticCode.TYPE_RAW_GENERIC_TYPE);
    }

    @Test
    public void wrongTypeArgumentCountIsRejected() {
        assertThat(first(checkFails(BOX + """
                func f() {
                    var box: Box<Integer, String> = Box(5)
                }

                """)).code()).isEqualTo(DiagnosticCode.TYPE_TYPE_ARGUMENT_ARITY);
    }

    @Test
    public void typeArgumentsOnANonGenericTypeAreRejected() {
        assertThat(first(checkFails("""
                func f() {
                    var x: Integer<String> = 5
                }

                """)).code()).isEqualTo(DiagnosticCode.TYPE_NOT_GENERIC);
    }

    @Test
    public void unknownTypeArgumentIsRejected() {
        assertThat(first(checkFails(BOX + """
                func f() {
                    var box: Box<Widget> = Box(5)
                }

                """)).code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }

    @Test
    public void aTypeParameterCannotTakeTypeArguments() {
        assertThat(first(checkFails("""
                func f<T>(x: T<Integer>) {
                }

                """)).code()).isEqualTo(DiagnosticCode.TYPE_NOT_GENERIC);
    }

    @Test
    public void typeArgumentsAreInvariant() {
        assertThat(first(checkFails(BOX + """
                func f() {
                    var box: Box<Integer> = Box("x")
                }

                """)).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void typeArgumentsAreNotCovariant() {
        assertThat(first(checkFails(BOX + """
                func f() {
                    var box: Box<Any> = Box("x")
                }

                """)).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void inferredTypeArgumentMustMatchTheCallContext() {
        assertThat(first(checkFails("""
                func identity<T>(x: T): T {
                    return x
                }
                func f(): String {
                    return identity(5)
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_RETURN_MISMATCH);
    }

    @Test
    public void uninferableTypeArgumentIsRejected() {
        assertThat(first(checkFails(BOX + """
                func unwrap<T>(box: Box<T>): T {
                    return box.value
                }
                func f(): Integer {
                    return unwrap(5)
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_CANNOT_INFER);
    }

    @Test
    public void duplicateTypeParameterNamesAreRejected() {
        assertThat(first(checkFails("""
                class Pair<T, T> {
                    var mutable value: T

                    Pair(value: T) {
                        this.value = value
                    }
                }
                """)).code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void typeTestAgainstAnErasedTypeArgumentIsRejected() {
        assertThat(first(checkFails(BOX + """
                func f(value: Any): Boolean {
                    return (value is Box<String>)
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ERASED_TYPE_TEST);
    }

    @Test
    public void castAgainstAnErasedTypeArgumentIsRejected() {
        assertThat(first(checkFails(BOX + """
                func f(value: Any): Box<String> {
                    return (value as Box<String>)
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ERASED_TYPE_TEST);
    }

    @Test
    public void listSizeIsImmutable() {
        assertThat(first(checkFails("""
                func f(values: List<Integer>) {
                    values.size = 5
                }

                """)).code()).isEqualTo(DiagnosticCode.TYPE_ASSIGN_TO_IMMUTABLE);
    }

    @Test
    public void listGetRequiresAnIntegerIndex() {
        assertThat(first(checkFails("""
                func f(values: List<Integer>): Integer {
                    return values.get("x")
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void listElementTypeIsEnforced() {
        assertThat(first(checkFails("""
                func f(values: List<Integer>): String {
                    return values.get(0)
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_RETURN_MISMATCH);
    }

    @Test
    public void listIsInvariantInItsElementType() {
        assertThat(first(checkFails("""
                func f(values: List<String>) {
                    var ints: List<Integer> = values
                }

                """)).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void listHasNoUnknownMembers() {
        assertThat(first(checkFails("""
                func f(values: List<Integer>): Integer {
                    return values.length
                }
                """)).code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void genericInterfaceRequirementMustBeImplemented() {
        assertThat(first(checkFails("""
                interface Container<T> {
                    method get(): T
                }
                class StringBox implements Container<String> {
                }

                """)).code()).isEqualTo(DiagnosticCode.SEM_MISSING_INTERFACE_IMPLEMENTATION);
    }

    @Test
    public void genericInterfaceImplementationReturnTypeIsChecked() {
        assertThat(first(checkFails("""
                interface Container<T> {
                    method get(): T
                }
                class StringBox implements Container<String> {
                    method get(): Integer {
                        return 1
                    }
                }

                """)).code()).isEqualTo(DiagnosticCode.SEM_IMPLEMENTATION_SIGNATURE);
    }

    @Test
    public void genericInterfaceImplementationParameterTypeIsChecked() {
        assertThat(first(checkFails("""
                interface Consumer<T> {
                    method accept(value: T)
                }
                class StringConsumer implements Consumer<String> {
                    method accept(value: Integer) {
                    }
                }

                """)).code()).isEqualTo(DiagnosticCode.SEM_IMPLEMENTATION_SIGNATURE);
    }

    @Test
    public void typeArgumentCountMismatchInImplementsIsRejected() {
        assertThat(first(checkFails("""
                interface Container<T> {
                    method get(): T
                }
                class StringBox implements Container<String, Integer> {
                    method get(): String {
                        return "x"
                    }
                }

                """)).code()).isEqualTo(DiagnosticCode.TYPE_TYPE_ARGUMENT_ARITY);
    }

    @Test
    public void wrongArgumentTypeForGenericFunctionIsRejected() {
        assertThat(first(checkFails("""
                func identity<T>(x: T): T {
                    return x
                }
                func f() {
                    var y: Integer = identity("x")
                }

                """)).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }
}
