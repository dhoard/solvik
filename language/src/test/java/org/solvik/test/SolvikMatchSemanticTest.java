/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.parseOk;

import org.junit.Test;
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
import org.solvik.type.IntType;
import org.solvik.type.ObjectType;
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
        assertTrue("analysis must succeed: " + result.diagnostics().all(), result.isSuccess());
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
                    Ok(Int)
                    Error(String)
                }
                func message(result: Result): String {
                    return match result {
                        Ok(value) => "value=" + "x"
                        Error(error) => "error=" + error
                    }
                }
                """);
        assertSame(StringType.INSTANCE, typeOfMatch(program, "message", 0));
    }

    @Test
    public void wildcardCoversVariantsThatHaveNoBranch() {
        CheckedProgram program = check("""
                enum Color {
                    Red
                    Blue
                    Green
                }
                func label(color: Color): Int {
                    return match color {
                        Red => 1
                        _ => 0
                    }
                }
                """);
        assertSame(IntType.INSTANCE, typeOfMatch(program, "label", 0));
    }

    @Test
    public void enumBindingHasTheSubstitutedValueType() {
        CheckedProgram program = check("""
                enum Option<T> {
                    Some(T)
                    None
                }
                func value(option: Option<Int>): Int {
                    return match option {
                        Some(x) => x
                        None => 0
                    }
                }
                """);
        EnumPatternNode some = (EnumPatternNode) matchInReturn(program, "value", 0).branches().get(0).pattern();
        BindingPatternNode binding = (BindingPatternNode) some.arguments().get(0);
        assertSame(IntType.INSTANCE, program.patternBindingOf(binding).orElseThrow().type());
        assertTrue(program.enumPatternOf(some).isPresent());
        assertSame(program.enumSymbol("Option").orElseThrow().type(), program.enumPatternOf(some).orElseThrow().owner().type());
    }

    @Test
    public void sealedSubtypeBindingNarrowsToTheSubtype() {
        CheckedProgram program = check("""
                sealed class Shape {
                }
                class Circle extends Shape {
                    val radius: Int

                    init(radius: Int) {
                        this.radius = radius
                    }
                }
                func area(shape: Shape): Int {
                    return match shape {
                        circle: Circle => circle.radius * circle.radius
                    }
                }
                """);
        BindingPatternNode circle = (BindingPatternNode) matchInReturn(program, "area", 0).branches().get(0).pattern();
        assertSame(program.classSymbol("Circle").orElseThrow().type(), program.patternBindingOf(circle).orElseThrow().type());
        assertSame(program.classSymbol("Circle").orElseThrow().type(), program.bindingTypeOf(circle).orElseThrow());
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
        assertSame(program.classSymbol("Shape").orElseThrow().type(), typeOfMatch(program, "pick", 0));
    }

    @Test
    public void unrelatedScalarBranchesUnifyToObject() {
        CheckedProgram program = check("""
                enum Value {
                    Number(Int)
                    Text(String)
                }
                func unwrap(value: Value): Object {
                    return match value {
                        Number(number) => number
                        Text(text) => text
                    }
                }
                """);
        assertSame(ObjectType.INSTANCE, typeOfMatch(program, "unwrap", 0));
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
        assertSame(StringType.INSTANCE.nullableView(), typeOfMatch(program, "unwrap", 0));
    }

    @Test
    public void nestedVariantBindingHasItsSubstitutedType() {
        CheckedProgram program = check("""
                enum Inner {
                    Some(Int)
                    None
                }
                enum Outer {
                    Wrap(Inner)
                }
                func value(outer: Outer): Int {
                    return match outer {
                        Wrap(Some(inner)) => inner
                        Wrap(None) => 0
                    }
                }
                """);
        EnumPatternNode wrap = (EnumPatternNode) matchInReturn(program, "value", 0).branches().get(0).pattern();
        EnumPatternNode some = (EnumPatternNode) wrap.arguments().get(0);
        BindingPatternNode inner = (BindingPatternNode) some.arguments().get(0);
        assertSame(IntType.INSTANCE, program.patternBindingOf(inner).orElseThrow().type());
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
        assertSame(StringType.INSTANCE, typeOfMatch(program, "name", 0));
    }

    @Test
    public void aTypedBindingMayCoverTheWholeEnum() {
        CheckedProgram program = check("""
                enum Color {
                    Red
                    Blue
                }
                func label(color: Color): Int {
                    return match color {
                        any: Color => 1
                    }
                }
                """);
        assertSame(IntType.INSTANCE, typeOfMatch(program, "label", 0));
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
        assertSame(StringType.INSTANCE, typeOfMatch(program, "name", 0));
    }

    @Test
    public void everyBranchPatternIsRecorded() {
        CheckedProgram program = check("""
                enum Color {
                    Red
                    Blue
                }
                func label(color: Color): Int {
                    return match color {
                        Red => 1
                        Blue => 2
                    }
                }
                """);
        MatchExprNode match = matchInReturn(program, "label", 0);
        for (MatchBranchNode branch : match.branches()) {
            EnumPatternNode pattern = (EnumPatternNode) branch.pattern();
            assertTrue(program.enumPatternOf(pattern).isPresent());
        }
    }
}
