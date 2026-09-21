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

import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.MatchBranchNode;
import org.solvik.ast.expression.MatchExprNode;
import org.solvik.ast.pattern.BindingPatternNode;
import org.solvik.ast.pattern.EnumPatternNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.IntegerType;
import org.solvik.type.AnyType;
import org.solvik.type.StringType;
import org.solvik.type.Type;

/**
 * Positive Phase 13 semantic tests (docs/LANGUAGE_SPEC.md section 12): branch pattern checking,
 * closed-set exhaustiveness, binding types, and branch result unification.
 */
public final class SolvikMatchSemanticTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("match.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
        return result.requireProgram();
    }

    private static FunctionDeclNode function(CheckedProgram program, String name) {
        for (var declaration : program.unit().declarations()) {
            if (declaration instanceof FunctionDeclNode function && function.name().equals(name)) {
                return function;
            }
        }
        throw new AssertionError("no function named " + name);
    }

    private static MatchExprNode matchInReturn(CheckedProgram program, String functionName, int statementIndex) {
        ReturnStmtNode statement = (ReturnStmtNode) function(program, functionName).body().statements().get(statementIndex);
        return (MatchExprNode) statement.value().orElseThrow();
    }

    private static Type typeOfMatch(CheckedProgram program, String functionName, int statementIndex) {
        return program.typeOf(matchInReturn(program, functionName, statementIndex)).orElseThrow();
    }

    @Test
    public void exhaustiveEnumMatchHasTheBranchResultType() {
        CheckedProgram program = check("""
                enum Result {
                    Ok(Integer)
                    Error(String)
                }
                func message(result: Result): String {
                    return match result {
                        Ok(value) => "value=" .. "x"
                        Error(error) => "error=" .. error
                    }
                }
                """);
        assertThat(typeOfMatch(program, "message", 0)).isSameAs(StringType.INSTANCE);
    }

    @Test
    public void wildcardCoversVariantsThatHaveNoBranch() {
        CheckedProgram program = check("""
                enum Color {
                    Red
                    Blue
                    Green
                }
                func label(color: Color): Integer {
                    return match color {
                        Red => 1
                        _ => 0
                    }
                }
                """);
        assertThat(typeOfMatch(program, "label", 0)).isSameAs(IntegerType.INSTANCE);
    }

    @Test
    public void enumBindingHasTheSubstitutedValueType() {
        CheckedProgram program = check("""
                enum Option<T> {
                    Some(T)
                    None
                }
                func value(option: Option<Integer>): Integer {
                    return match option {
                        Some(x) => x
                        None => 0
                    }
                }
                """);
        EnumPatternNode some = (EnumPatternNode) matchInReturn(program, "value", 0).branches().get(0).pattern();
        BindingPatternNode binding = (BindingPatternNode) some.arguments().get(0);
        assertThat(program.patternBindingOf(binding).orElseThrow().type()).isSameAs(IntegerType.INSTANCE);
        assertThat(program.enumPatternOf(some).isPresent()).isTrue();
        assertThat(program.enumPatternOf(some).orElseThrow().owner().type()).isSameAs(program.enumSymbol("Option").orElseThrow().type());
    }

    @Test
    public void sealedSubtypeBindingNarrowsToTheSubtype() {
        CheckedProgram program = check("""
                sealed class Shape {
                }
                class Circle extends Shape {
                    val radius: Integer

                    Circle(radius: Integer) {
                        this.radius = radius
                    }
                }
                func area(shape: Shape): Integer {
                    return match shape {
                        circle: Circle => circle.radius * circle.radius
                    }
                }
                """);
        BindingPatternNode circle = (BindingPatternNode) matchInReturn(program, "area", 0).branches().get(0).pattern();
        assertThat(program.patternBindingOf(circle).orElseThrow().type()).isSameAs(program.classSymbol("Circle").orElseThrow().type());
        assertThat(program.bindingTypeOf(circle).orElseThrow()).isSameAs(program.classSymbol("Circle").orElseThrow().type());
    }

    @Test
    public void subtypeBranchesUnifyToTheSealedSupertype() {
        CheckedProgram program = check("""
                sealed class Shape {
                }
                class Circle extends Shape {
                }
                class Square extends Shape {
                }
                func pick(shape: Shape): Shape {
                    return match shape {
                        circle: Circle => circle
                        square: Square => square
                    }
                }
                """);
        assertThat(typeOfMatch(program, "pick", 0)).isSameAs(program.classSymbol("Shape").orElseThrow().type());
    }

    @Test
    public void unrelatedScalarBranchesUnifyToAny() {
        CheckedProgram program = check("""
                enum Value {
                    Number(Integer)
                    Text(String)
                }
                func unwrap(value: Value): Any {
                    return match value {
                        Number(number) => number
                        Text(text) => text
                    }
                }
                """);
        assertThat(typeOfMatch(program, "unwrap", 0)).isSameAs(AnyType.INSTANCE);
    }

    @Test
    public void nullAndNonNullBranchesUnifyToANullableType() {
        CheckedProgram program = check("""
                enum Value {
                    Missing
                    Text(String)
                }
                func unwrap(value: Value): String? {
                    return match value {
                        Missing => null
                        Text(text) => text
                    }
                }
                """);
        assertThat(typeOfMatch(program, "unwrap", 0)).isSameAs(StringType.INSTANCE.nullableView());
    }

    @Test
    public void nestedVariantBindingHasItsSubstitutedType() {
        CheckedProgram program = check("""
                enum Inner {
                    Some(Integer)
                    None
                }
                enum Outer {
                    Wrap(Inner)
                }
                func value(outer: Outer): Integer {
                    return match outer {
                        Wrap(Some(inner)) => inner
                        Wrap(None) => 0
                    }
                }
                """);
        EnumPatternNode wrap = (EnumPatternNode) matchInReturn(program, "value", 0).branches().get(0).pattern();
        EnumPatternNode some = (EnumPatternNode) wrap.arguments().get(0);
        BindingPatternNode inner = (BindingPatternNode) some.arguments().get(0);
        assertThat(program.patternBindingOf(inner).orElseThrow().type()).isSameAs(IntegerType.INSTANCE);
    }

    @Test
    public void matchOnANullableSealedTypeIsExhaustiveWithAWildcard() {
        CheckedProgram program = check("""
                sealed class Shape {
                }
                class Circle extends Shape {
                }
                func name(shape: Shape?): String {
                    return match shape {
                        circle: Circle => "circle"
                        _ => "none"
                    }
                }
                """);
        assertThat(typeOfMatch(program, "name", 0)).isSameAs(StringType.INSTANCE);
    }

    @Test
    public void aTypedBindingMayCoverTheWholeEnum() {
        CheckedProgram program = check("""
                enum Color {
                    Red
                    Blue
                }
                func label(color: Color): Integer {
                    return match color {
                        any: Color => 1
                    }
                }
                """);
        assertThat(typeOfMatch(program, "label", 0)).isSameAs(IntegerType.INSTANCE);
    }

    @Test
    public void aWildcardAfterATypedBindingOnANullableTypeIsReachable() {
        CheckedProgram program = check("""
                sealed class Shape {
                }
                class Circle extends Shape {
                }
                func name(shape: Shape?): String {
                    return match shape {
                        full: Shape => "shape"
                        _ => "none"
                    }
                }
                """);
        assertThat(typeOfMatch(program, "name", 0)).isSameAs(StringType.INSTANCE);
    }

    @Test
    public void everyBranchPatternIsRecorded() {
        CheckedProgram program = check("""
                enum Color {
                    Red
                    Blue
                }
                func label(color: Color): Integer {
                    return match color {
                        Red => 1
                        Blue => 2
                    }
                }
                """);
        MatchExprNode match = matchInReturn(program, "label", 0);
        for (MatchBranchNode branch : match.branches()) {
            EnumPatternNode pattern = (EnumPatternNode) branch.pattern();
            assertThat(program.enumPatternOf(pattern).isPresent()).isTrue();
        }
    }
}
