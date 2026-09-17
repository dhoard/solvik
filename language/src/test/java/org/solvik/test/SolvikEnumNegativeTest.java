/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
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
    public void anUnknownVariantIsRejected() {
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_MEMBER, first(checkFails(RESULT + """
                fun f(): Result {
                    return Result.Missing(1)
                }
                """)).code());
    }

    @Test
    public void anUnknownValueLessVariantIsRejected() {
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_MEMBER, first(checkFails(RESULT + """
                fun f(): Result {
                    return Result.Missing
                }
                """)).code());
    }

    @Test
    public void constructingAVariantWithTheWrongArityIsRejected() {
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(checkFails(RESULT + """
                fun f(): Result {
                    return Result.Ok(1, 2)
                }
                """)).code());
    }

    @Test
    public void aValueCarryingVariantCannotBeUsedBare() {
        assertEquals(DiagnosticCode.TYPE_ARITY_MISMATCH, first(checkFails(RESULT + """
                fun f(): Result {
                    return Result.Ok
                }
                """)).code());
    }

    @Test
    public void aVariantValueWithTheWrongTypeIsRejected() {
        assertEquals(DiagnosticCode.TYPE_MISMATCH, first(checkFails(RESULT + """
                fun f(): Result {
                    return Result.Ok("x")
                }
                """)).code());
    }

    @Test
    public void anEnumNameCannotBeConstructedDirectly() {
        assertEquals(DiagnosticCode.TYPE_ENUM_AS_VALUE, first(checkFails(RESULT + """
                fun f(): Result {
                    return Result(1)
                }
                """)).code());
    }

    @Test
    public void anEnumNameCannotBeUsedAsAValue() {
        assertEquals(DiagnosticCode.TYPE_ENUM_AS_VALUE, first(checkFails(RESULT + """
                fun f(): Unit {
                    val r: Result = Result
                }
                """)).code());
    }

    @Test
    public void aSealedClassCannotBeConstructed() {
        assertEquals(DiagnosticCode.SEM_CANNOT_CONSTRUCT_SEALED, first(checkFails("""
                sealed class Shape {
                }
                fun f(): Shape {
                    return Shape()
                }
                """)).code());
    }

    @Test
    public void aClassMayNotExtendAnEnum() {
        assertEquals(DiagnosticCode.SEM_INVALID_SUPERCLASS, first(checkFails(RESULT + """
                class Extra extends Result {
                }
                """)).code());
    }

    @Test
    public void aClassMayNotImplementAnEnum() {
        assertEquals(DiagnosticCode.SEM_INVALID_INTERFACE, first(checkFails(RESULT + """
                class Extra implements Result {
                }
                """)).code());
    }

    @Test
    public void duplicateVariantNamesAreRejected() {
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, first(checkFails("""
                enum Broken {
                    Same(Int)
                    Same(String)
                }
                """)).code());
    }

    @Test
    public void anEnumNameCollidesWithAClassName() {
        assertEquals(DiagnosticCode.RESOL_DUPLICATE_NAME, first(checkFails("""
                class Result {
                }
                enum Result {
                    Ok(Int)
                }
                """)).code());
    }

    @Test
    public void aRawGenericEnumTypeIsRejected() {
        assertEquals(DiagnosticCode.TYPE_RAW_GENERIC_TYPE, first(checkFails("""
                enum Option<T> {
                    Some(T)
                }
                fun f(o: Option): Unit {
                }
                """)).code());
    }

    @Test
    public void aValueLessVariantOfAGenericEnumCannotInferItsArgument() {
        assertEquals(DiagnosticCode.TYPE_CANNOT_INFER, first(checkFails("""
                enum Option<T> {
                    Some(T)
                    None
                }
                fun f(): Unit {
                    val none = Option.None
                }
                """)).code());
    }

    @Test
    public void anUnknownVariantValueTypeIsRejected() {
        assertEquals(DiagnosticCode.RESOL_UNKNOWN_TYPE, first(checkFails("""
                enum Broken {
                    Value(Widget)
                }
                """)).code());
    }

    @Test
    public void anEnumValueIsNotAssignableToAnUnrelatedEnumType() {
        assertEquals(DiagnosticCode.TYPE_RETURN_MISMATCH, first(checkFails("""
                enum Color {
                    Red
                }
                enum Shape {
                    Square
                }
                fun f(): Color {
                    return Shape.Square
                }
                """)).code());
    }
}
