/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.Test;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.ClassSymbol;
import org.solvik.semantic.EnumSymbol;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.AnyType;
import org.solvik.type.BooleanType;
import org.solvik.type.EnumType;
import org.solvik.type.IntType;
import org.solvik.type.ObjectType;
import org.solvik.type.ParameterizedType;
import org.solvik.type.StringType;
import org.solvik.type.Type;
import org.solvik.type.TypeParameterType;

/**
 * Positive Phase 12 semantic tests (docs/LANGUAGE_SPEC.md section 12): enum variant construction,
 * generic variant inference, value equality typing, and the closed metadata of enums and sealed
 * classes.
 */
public final class SolvikEnumSemanticTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("enum.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertTrue("analysis must succeed: " + result.diagnostics().all(), result.isSuccess());
        return result.requireProgram();
    }

    private static FunctionDeclNode function(CheckedProgram program, String name) {
        for (AstNode declaration : program.unit().declarations()) {
            if (declaration instanceof FunctionDeclNode function && function.name().equals(name)) {
                return function;
            }
        }
        throw new AssertionError("no function named " + name);
    }

    private static Type typeOfReturn(CheckedProgram program, String functionName, int statementIndex) {
        FunctionDeclNode function = function(program, functionName);
        ReturnStmtNode statement = (ReturnStmtNode) function.body().statements().get(statementIndex);
        return program.typeOf(statement.value().orElseThrow()).orElseThrow();
    }

    private static ExpressionNode initializer(CheckedProgram program, String functionName, int statementIndex) {
        LocalDeclNode declaration = (LocalDeclNode) function(program, functionName).body().statements().get(statementIndex);
        return declaration.initializer();
    }

    @Test
    public void enumVariantsAreTheCompletePermittedSetInSourceOrder() {
        CheckedProgram program = check("""
                enum Result {
                    Ok(Int)
                    Error(String)
                }
                """);
        EnumSymbol result = program.enumSymbol("Result").orElseThrow();
        assertTrue(result.type() instanceof EnumType);
        assertEquals(List.of("Ok", "Error"), result.variants().stream().map(v -> v.name()).toList());
        assertEquals(List.of(IntType.INSTANCE), result.variants().get(0).valueTypes());
        assertEquals(List.of(StringType.INSTANCE), result.variants().get(1).valueTypes());
        assertSame(result, program.enumOf(result.declaration()).orElseThrow());
    }

    @Test
    public void enumValuesSitUnderObjectAndAny() {
        CheckedProgram program = check("""
                enum Color {
                    Red
                }
                """);
        EnumType color = program.enumSymbol("Color").orElseThrow().type();
        assertTrue(color.isSubtypeOf(ObjectType.INSTANCE));
        assertTrue(color.isSubtypeOf(AnyType.INSTANCE));
        assertFalse(ObjectType.INSTANCE.isSubtypeOf(color));
        assertFalse(color.isSubtypeOf(StringType.INSTANCE));
    }

    @Test
    public void qualifiedVariantConstructionHasTheEnumType() {
        CheckedProgram program = check("""
                enum Result {
                    Ok(Int)
                    Error(String)
                }
                func make(): Result {
                    return Result.Ok(1)
                }
                """);
        assertEquals(program.enumSymbol("Result").orElseThrow().type(), typeOfReturn(program, "make", 0));
    }

    @Test
    public void valueLessVariantIsConstructedWithoutACall() {
        CheckedProgram program = check("""
                enum Color {
                    Red
                    Blue
                }
                func make(): Color {
                    return Color.Red
                }
                """);
        assertEquals(program.enumSymbol("Color").orElseThrow().type(), typeOfReturn(program, "make", 0));
    }

    @Test
    public void genericVariantConstructionInfersTheEnumTypeArgument() {
        CheckedProgram program = check("""
                enum Option<T> {
                    Some(T)
                }
                func make(): Option<Int> {
                    return Option.Some(1)
                }
                """);
        EnumType option = program.enumSymbol("Option").orElseThrow().type();
        Type inferred = typeOfReturn(program, "make", 0);
        assertTrue(inferred instanceof ParameterizedType);
        assertSame(option, ((ParameterizedType) inferred).base());
        assertEquals(IntType.INSTANCE, ((ParameterizedType) inferred).arguments().get(0));
    }

    @Test
    public void aVariantMayCarrySeveralValuesAndSubstituteEach() {
        CheckedProgram program = check("""
                enum Pair<T> {
                    Both(T, T)
                }
                func make(): Pair<String> {
                    return Pair.Both("a", "b")
                }
                """);
        Type inferred = typeOfReturn(program, "make", 0);
        assertTrue(inferred instanceof ParameterizedType);
        assertEquals(StringType.INSTANCE, ((ParameterizedType) inferred).arguments().get(0));
    }

    @Test
    public void enumValuesAreAssignableToTheirDeclaredEnumType() {
        CheckedProgram program = check("""
                enum Color {
                    Red
                }
                func describe(color: Color): Unit {
                }
                func use(): Unit {
                    val color: Color = Color.Red
                    describe(color)
                }
                """);
        Type initializerType = program.typeOf(initializer(program, "use", 0)).orElseThrow();
        assertEquals(program.enumSymbol("Color").orElseThrow().type(), initializerType);
    }

    @Test
    public void enumEqualityYieldsBoolean() {
        CheckedProgram program = check("""
                enum Color {
                    Red
                    Blue
                }
                func same(a: Color, b: Color): Boolean {
                    return a == b
                }
                """);
        assertEquals(BooleanType.INSTANCE, typeOfReturn(program, "same", 0));
    }

    @Test
    public void enumTypesParticipateInTypeTests() {
        CheckedProgram program = check("""
                enum Color {
                    Red
                }
                func isColor(value: Any): Boolean {
                    return value is Color
                }
                """);
        assertEquals(BooleanType.INSTANCE, typeOfReturn(program, "isColor", 0));
        ReturnStmtNode statement = (ReturnStmtNode) function(program, "isColor").body().statements().get(0);
        assertEquals(program.enumSymbol("Color").orElseThrow().type(), program.testedTypeOf(statement.value().orElseThrow()).orElseThrow());
    }

    @Test
    public void sealedClassRecordsItsCompleteTransitiveSubtypeSet() {
        CheckedProgram program = check("""
                sealed class Shape {
                }
                open class Circle extends Shape {
                }
                class UnitCircle extends Circle {
                }
                class Square extends Shape {
                }
                class Unrelated {
                }
                """);
        ClassSymbol shape = program.classSymbol("Shape").orElseThrow();
        assertTrue(shape.isSealed());
        assertTrue(shape.isExtendable());
        List<String> permitted = shape.permittedSubtypes().stream().map(ClassSymbol::name).sorted().toList();
        assertEquals(List.of("Circle", "Square"), permitted);
        List<String> all = shape.allSubtypes().stream().map(ClassSymbol::name).sorted().toList();
        assertEquals(List.of("Circle", "Square", "UnitCircle"), all);
        assertFalse(program.classSymbol("Unrelated").orElseThrow().isSealed());
        assertTrue(program.classSymbol("Unrelated").orElseThrow().permittedSubtypes().isEmpty());
        assertTrue(program.classSymbol("Unrelated").orElseThrow().allSubtypes().isEmpty());
    }

    @Test
    public void sealedClassIsNotConstructedButItsSubtypesAre() {
        CheckedProgram program = check("""
                sealed class Shape {
                    open func name(): String {
                        return "shape"
                    }
                }
                class Circle extends Shape {
                    override func name(): String {
                        return "circle"
                    }
                }
                func make(): Shape {
                    return Circle()
                }
                """);
        Type constructed = typeOfReturn(program, "make", 0);
        assertEquals(program.classSymbol("Circle").orElseThrow().type(), constructed);
    }

    @Test
    public void enumDeclarationsMayReferenceAnyNominalType() {
        CheckedProgram program = check("""
                class Payload {
                    val value: Int

                    init(value: Int) {
                        this.value = value
                    }
                }
                enum Event {
                    Received(Payload)
                }
                func make(p: Payload): Event {
                    return Event.Received(p)
                }
                """);
        EnumSymbol event = program.enumSymbol("Event").orElseThrow();
        assertEquals(program.classSymbol("Payload").orElseThrow().type(), event.variants().get(0).valueTypes().get(0));
        assertEquals(event.type(), typeOfReturn(program, "make", 0));
    }

    @Test
    public void genericEnumApplicationIsCanonicalAndInvariant() {
        CheckedProgram program = check("""
                enum Option<T> {
                    Some(T)
                }
                """);
        EnumType option = program.enumSymbol("Option").orElseThrow().type();
        TypeParameterType parameter = option.typeParameters().get(0);
        assertEquals("T", parameter.name());
        Type intOption = option.parameterizedView(List.of(IntType.INSTANCE));
        Type stringOption = option.parameterizedView(List.of(StringType.INSTANCE));
        assertSame("applications are canonical", intOption, option.parameterizedView(List.of(IntType.INSTANCE)));
        assertTrue(intOption.isAssignableTo(intOption));
        assertFalse(intOption.isAssignableTo(stringOption));
        assertFalse(stringOption.isAssignableTo(intOption));
        assertTrue(intOption.isAssignableTo(ObjectType.INSTANCE));
    }
}
