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
 * Negative Phase 12 semantic tests (docs/LANGUAGE_SPEC.md section 12): invalid enum variant
 * construction, enum use as a value, sealed construction, extending a closed enum, and the closed
 * metadata that makes an external or duplicate variant impossible.
 */
public final class SolvikEnumNegativeTest {

    private static final String RESULT = """
            enum Result {
                Ok(Int)
                Error(String)
            }
            """;

    private static DiagnosticBag checkFails(String text) {
        CompilationUnitNode unit = parseOk("eneg.sol", text);
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
    public void anUnknownVariantIsRejected() {
        assertThat(first(checkFails(RESULT + """
                func f(): Result {
                    return Result.Missing(1)
                }
                """)).code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void anUnknownValueLessVariantIsRejected() {
        assertThat(first(checkFails(RESULT + """
                func f(): Result {
                    return Result.Missing
                }
                """)).code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_MEMBER);
    }

    @Test
    public void constructingAVariantWithTheWrongArityIsRejected() {
        assertThat(first(checkFails(RESULT + """
                func f(): Result {
                    return Result.Ok(1, 2)
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void aValueCarryingVariantCannotBeUsedBare() {
        assertThat(first(checkFails(RESULT + """
                func f(): Result {
                    return Result.Ok
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void aVariantValueWithTheWrongTypeIsRejected() {
        assertThat(first(checkFails(RESULT + """
                func f(): Result {
                    return Result.Ok("x")
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_MISMATCH);
    }

    @Test
    public void anEnumNameCannotBeConstructedDirectly() {
        assertThat(first(checkFails(RESULT + """
                func f(): Result {
                    return Result(1)
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ENUM_AS_VALUE);
    }

    @Test
    public void anEnumNameCannotBeUsedAsAValue() {
        assertThat(first(checkFails(RESULT + """
                func f(): Unit {
                    val r: Result = Result
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_ENUM_AS_VALUE);
    }

    @Test
    public void aSealedClassCannotBeConstructed() {
        assertThat(first(checkFails("""
                sealed class Shape {
                }
                func f(): Shape {
                    return Shape()
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_CANNOT_CONSTRUCT_SEALED);
    }

    @Test
    public void aClassMayNotExtendAnEnum() {
        assertThat(first(checkFails(RESULT + """
                class Extra extends Result {
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_INVALID_SUPERCLASS);
    }

    @Test
    public void aClassMayNotImplementAnEnum() {
        assertThat(first(checkFails(RESULT + """
                class Extra implements Result {
                }
                """)).code()).isEqualTo(DiagnosticCode.SEM_INVALID_INTERFACE);
    }

    @Test
    public void duplicateVariantNamesAreRejected() {
        assertThat(first(checkFails("""
                enum Broken {
                    Same(Int)
                    Same(String)
                }
                """)).code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void anEnumNameCollidesWithAClassName() {
        assertThat(first(checkFails("""
                class Result {
                }
                enum Result {
                    Ok(Int)
                }
                """)).code()).isEqualTo(DiagnosticCode.RESOL_DUPLICATE_NAME);
    }

    @Test
    public void aRawGenericEnumTypeIsRejected() {
        assertThat(first(checkFails("""
                enum Option<T> {
                    Some(T)
                }
                func f(o: Option): Unit {
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_RAW_GENERIC_TYPE);
    }

    @Test
    public void aValueLessVariantOfAGenericEnumCannotInferItsArgument() {
        assertThat(first(checkFails("""
                enum Option<T> {
                    Some(T)
                    None
                }
                func f(): Unit {
                    val none = Option.None
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_CANNOT_INFER);
    }

    @Test
    public void anUnknownVariantValueTypeIsRejected() {
        assertThat(first(checkFails("""
                enum Broken {
                    Value(Widget)
                }
                """)).code()).isEqualTo(DiagnosticCode.RESOL_UNKNOWN_TYPE);
    }

    @Test
    public void anEnumValueIsNotAssignableToAnUnrelatedEnumType() {
        assertThat(first(checkFails("""
                enum Color {
                    Red
                }
                enum Shape {
                    Square
                }
                func f(): Color {
                    return Shape.Square
                }
                """)).code()).isEqualTo(DiagnosticCode.TYPE_RETURN_MISMATCH);
    }
}
