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
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.parseFails;
import static org.solvik.test.SolvikTestSupport.parseOk;

import org.junit.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.MatchBranchNode;
import org.solvik.ast.expression.MatchExprNode;
import org.solvik.ast.pattern.BindingPatternNode;
import org.solvik.ast.pattern.EnumPatternNode;
import org.solvik.ast.pattern.WildcardPatternNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;

/**
 * Phase 13 parser tests (docs/LANGUAGE_SPEC.md section 12): the {@code match} expression, enum
 * variant patterns, sealed-subtype binding patterns, and the wildcard {@code _}.
 */
public final class SolvikMatchParserTest {

    private static MatchExprNode matchInReturn(CompilationUnitNode unit, String functionName) {
        for (var declaration : unit.declarations()) {
            if (declaration instanceof FunctionDeclNode function && function.name().equals(functionName)) {
                ReturnStmtNode statement = (ReturnStmtNode) function.body().statements().get(0);
                return (MatchExprNode) statement.value().orElseThrow();
            }
        }
        throw new AssertionError("no function named " + functionName);
    }

    @Test
    public void matchExpressionRecordsScrutineeAndValueLessVariantPatterns() {
        CompilationUnitNode unit = parseOk("m.sol", """
                enum Color {
                    Red
                    Blue
                }
                func name(color: Color): String {
                    return match color {
                        Red => "red"
                        Blue => "blue"
                    }
                }
                """);
        MatchExprNode match = matchInReturn(unit, "name");
        assertEquals(AstKind.MATCH_EXPR, match.kind());
        assertEquals(AstKind.NAME_REF_EXPR, match.scrutinee().kind());
        assertEquals(2, match.branches().size());
        EnumPatternNode red = (EnumPatternNode) match.branches().get(0).pattern();
        assertEquals("Red", red.variantName());
        assertTrue(red.arguments().isEmpty());
        assertEquals("Blue", ((EnumPatternNode) match.branches().get(1).pattern()).variantName());
    }

    @Test
    public void valueCarryingVariantPatternDestructuresIntoBindings() {
        CompilationUnitNode unit = parseOk("m.sol", """
                enum Result {
                    Ok(Int)
                    Error(String)
                }
                func describe(result: Result): String {
                    return match result {
                        Ok(value) => "ok"
                        Error(message) => message
                    }
                }
                """);
        MatchExprNode match = matchInReturn(unit, "describe");
        EnumPatternNode ok = (EnumPatternNode) match.branches().get(0).pattern();
        assertEquals(1, ok.arguments().size());
        BindingPatternNode value = (BindingPatternNode) ok.arguments().get(0);
        assertEquals("value", value.name());
        assertTrue(value.typeRef().isEmpty());
    }

    @Test
    public void wildcardPatternIsRecorded() {
        CompilationUnitNode unit = parseOk("m.sol", """
                enum Color {
                    Red
                }
                func name(color: Color): String {
                    return match color {
                        Red => "red"
                        _ => "other"
                    }
                }
                """);
        MatchExprNode match = matchInReturn(unit, "name");
        assertEquals(AstKind.WILDCARD_PATTERN, match.branches().get(1).pattern().kind());
    }

    @Test
    public void sealedSubtypeBindingPatternRecordsItsWrittenType() {
        CompilationUnitNode unit = parseOk("m.sol", """
                sealed class Shape {
                }
                class Circle extends Shape {
                }
                func describe(shape: Shape): String {
                    return match shape {
                        circle: Circle => "circle"
                    }
                }
                """);
        MatchExprNode match = matchInReturn(unit, "describe");
        BindingPatternNode circle = (BindingPatternNode) match.branches().get(0).pattern();
        assertEquals("circle", circle.name());
        assertEquals("Circle", circle.typeRef().orElseThrow().name());
    }

    @Test
    public void nestedVariantPatternsAreRecorded() {
        CompilationUnitNode unit = parseOk("m.sol", """
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
        MatchExprNode match = matchInReturn(unit, "value");
        EnumPatternNode wrap = (EnumPatternNode) match.branches().get(0).pattern();
        EnumPatternNode some = (EnumPatternNode) wrap.arguments().get(0);
        assertEquals("Some", some.variantName());
        assertEquals("inner", ((BindingPatternNode) some.arguments().get(0)).name());
    }

    @Test
    public void wildcardInsideAVariantIsRecorded() {
        CompilationUnitNode unit = parseOk("m.sol", """
                enum Result {
                    Ok(Int)
                }
                func value(result: Result): Int {
                    return match result {
                        Ok(_) => 1
                    }
                }
                """);
        MatchExprNode match = matchInReturn(unit, "value");
        EnumPatternNode ok = (EnumPatternNode) match.branches().get(0).pattern();
        assertEquals(AstKind.WILDCARD_PATTERN, ok.arguments().get(0).kind());
    }

    @Test
    public void matchIsUsableAsALocalInitializer() {
        CompilationUnitNode unit = parseOk("m.sol", """
                enum Color {
                    Red
                }
                func run(color: Color): Unit {
                    val label = match color {
                        Red => "red"
                    }
                }
                """);
        FunctionDeclNode run = (FunctionDeclNode) unit.declarations().get(1);
        LocalDeclNode declaration = (LocalDeclNode) run.body().statements().get(0);
        assertEquals(AstKind.MATCH_EXPR, declaration.initializer().kind());
    }

    @Test
    public void branchesKeepSourceOrder() {
        CompilationUnitNode unit = parseOk("m.sol", """
                enum Value {
                    A
                    B
                    C
                }
                func label(value: Value): Int {
                    return match value {
                        C => 3
                        A => 1
                        B => 2
                    }
                }
                """);
        MatchExprNode match = matchInReturn(unit, "label");
        assertEquals("C", ((EnumPatternNode) match.branches().get(0).pattern()).variantName());
        assertEquals("A", ((EnumPatternNode) match.branches().get(1).pattern()).variantName());
        assertEquals("B", ((EnumPatternNode) match.branches().get(2).pattern()).variantName());
    }

    @Test
    public void aBranchWithoutAnArrowIsRejected() {
        assertTrue(parseFails("m.sol", """
                enum Color {
                    Red
                }
                func name(color: Color): String {
                    return match color {
                        Red "red"
                    }
                }
                """).hasErrors());
    }

    @Test
    public void aMatchWithoutAScrutineeIsRejected() {
        assertTrue(parseFails("m.sol", """
                func name(): String {
                    return match {
                        _ => "x"
                    }
                }
                """).hasErrors());
    }

    @Test
    public void aMatchWithoutAClosingBraceIsRejected() {
        assertTrue(parseFails("m.sol", """
                func name(): String {
                    return match 1 {
                        _ => "x"
                }
                """).hasErrors());
    }

    @Test
    public void aMatchBranchWithoutAPatternIsRejected() {
        assertTrue(parseFails("m.sol", """
                func name(): String {
                    return match 1 {
                        => "x"
                    }
                }
                """).hasErrors());
    }

    @Test
    public void aTrailingCommaInsideAVariantPatternIsRejected() {
        assertTrue(parseFails("m.sol", """
                enum Result {
                    Ok(Int)
                }
                func value(result: Result): Int {
                    return match result {
                        Ok(value,) => value
                    }
                }
                """).hasErrors());
    }

    @Test
    public void matchIsAnExpressionKind() {
        CompilationUnitNode unit = parseOk("m.sol", """
                enum Color {
                    Red
                }
                func name(color: Color): String {
                    return match color {
                        Red => "red"
                    }
                }
                """);
        for (MatchBranchNode branch : matchInReturn(unit, "name").branches()) {
            assertEquals(AstKind.MATCH_BRANCH, branch.kind());
        }
    }
}
