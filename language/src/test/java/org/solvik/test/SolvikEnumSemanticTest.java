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
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
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
        assertThat(result.type() instanceof EnumType).isTrue();
        assertThat(result.variants().stream().map(v -> v.name()).toList()).isEqualTo(List.of("Ok", "Error"));
        assertThat(result.variants().get(0).valueTypes()).isEqualTo(List.of(IntType.INSTANCE));
        assertThat(result.variants().get(1).valueTypes()).isEqualTo(List.of(StringType.INSTANCE));
        assertThat(program.enumOf(result.declaration()).orElseThrow()).isSameAs(result);
    }

    @Test
    public void enumValuesSitUnderObjectAndAny() {
        CheckedProgram program = check("""
                enum Color {
                    Red
                }
                """);
        EnumType color = program.enumSymbol("Color").orElseThrow().type();
        assertThat(color.isSubtypeOf(ObjectType.INSTANCE)).isTrue();
        assertThat(color.isSubtypeOf(AnyType.INSTANCE)).isTrue();
        assertThat(ObjectType.INSTANCE.isSubtypeOf(color)).isFalse();
        assertThat(color.isSubtypeOf(StringType.INSTANCE)).isFalse();
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
        assertThat(typeOfReturn(program, "make", 0)).isEqualTo(program.enumSymbol("Result").orElseThrow().type());
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
        assertThat(typeOfReturn(program, "make", 0)).isEqualTo(program.enumSymbol("Color").orElseThrow().type());
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
        assertThat(inferred instanceof ParameterizedType).isTrue();
        assertThat(((ParameterizedType) inferred).base()).isSameAs(option);
        assertThat(((ParameterizedType) inferred).arguments().get(0)).isEqualTo(IntType.INSTANCE);
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
        assertThat(inferred instanceof ParameterizedType).isTrue();
        assertThat(((ParameterizedType) inferred).arguments().get(0)).isEqualTo(StringType.INSTANCE);
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
        assertThat(initializerType).isEqualTo(program.enumSymbol("Color").orElseThrow().type());
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
        assertThat(typeOfReturn(program, "same", 0)).isEqualTo(BooleanType.INSTANCE);
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
        assertThat(typeOfReturn(program, "isColor", 0)).isEqualTo(BooleanType.INSTANCE);
        ReturnStmtNode statement = (ReturnStmtNode) function(program, "isColor").body().statements().get(0);
        assertThat(program.testedTypeOf(statement.value().orElseThrow()).orElseThrow()).isEqualTo(program.enumSymbol("Color").orElseThrow().type());
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
        assertThat(shape.isSealed()).isTrue();
        assertThat(shape.isExtendable()).isTrue();
        List<String> permitted = shape.permittedSubtypes().stream().map(ClassSymbol::name).sorted().toList();
        assertThat(permitted).isEqualTo(List.of("Circle", "Square"));
        List<String> all = shape.allSubtypes().stream().map(ClassSymbol::name).sorted().toList();
        assertThat(all).isEqualTo(List.of("Circle", "Square", "UnitCircle"));
        assertThat(program.classSymbol("Unrelated").orElseThrow().isSealed()).isFalse();
        assertThat(program.classSymbol("Unrelated").orElseThrow().permittedSubtypes().isEmpty()).isTrue();
        assertThat(program.classSymbol("Unrelated").orElseThrow().allSubtypes().isEmpty()).isTrue();
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
        assertThat(constructed).isEqualTo(program.classSymbol("Circle").orElseThrow().type());
    }

    @Test
    public void enumDeclarationsMayReferenceAnyNominalType() {
        CheckedProgram program = check("""
                class Payload {
                    val value: Int

                    Payload(value: Int) {
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
        assertThat(event.variants().get(0).valueTypes().get(0)).isEqualTo(program.classSymbol("Payload").orElseThrow().type());
        assertThat(typeOfReturn(program, "make", 0)).isEqualTo(event.type());
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
        assertThat(parameter.name()).isEqualTo("T");
        Type intOption = option.parameterizedView(List.of(IntType.INSTANCE));
        Type stringOption = option.parameterizedView(List.of(StringType.INSTANCE));
        assertThat(option.parameterizedView(List.of(IntType.INSTANCE))).as("applications are canonical").isSameAs(intOption);
        assertThat(intOption.isAssignableTo(intOption)).isTrue();
        assertThat(intOption.isAssignableTo(stringOption)).isFalse();
        assertThat(stringOption.isAssignableTo(intOption)).isFalse();
        assertThat(intOption.isAssignableTo(ObjectType.INSTANCE)).isTrue();
    }
}
