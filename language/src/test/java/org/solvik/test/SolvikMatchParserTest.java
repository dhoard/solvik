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
import static org.solvik.test.SolvikTestSupport.parseFails;
import static org.solvik.test.SolvikTestSupport.parseOk;

import org.junit.jupiter.api.Test;
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
        assertThat(match.kind()).isEqualTo(AstKind.MATCH_EXPR);
        assertThat(match.scrutinee().kind()).isEqualTo(AstKind.NAME_REF_EXPR);
        assertThat(match.branches().size()).isEqualTo(2);
        EnumPatternNode red = (EnumPatternNode) match.branches().get(0).pattern();
        assertThat(red.variantName()).isEqualTo("Red");
        assertThat(red.arguments().isEmpty()).isTrue();
        assertThat(((EnumPatternNode) match.branches().get(1).pattern()).variantName()).isEqualTo("Blue");
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
        assertThat(ok.arguments().size()).isEqualTo(1);
        BindingPatternNode value = (BindingPatternNode) ok.arguments().get(0);
        assertThat(value.name()).isEqualTo("value");
        assertThat(value.typeRef().isEmpty()).isTrue();
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
        assertThat(match.branches().get(1).pattern().kind()).isEqualTo(AstKind.WILDCARD_PATTERN);
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
        assertThat(circle.name()).isEqualTo("circle");
        assertThat(circle.typeRef().orElseThrow().name()).isEqualTo("Circle");
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
        assertThat(some.variantName()).isEqualTo("Some");
        assertThat(((BindingPatternNode) some.arguments().get(0)).name()).isEqualTo("inner");
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
        assertThat(ok.arguments().get(0).kind()).isEqualTo(AstKind.WILDCARD_PATTERN);
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
        assertThat(declaration.initializer().kind()).isEqualTo(AstKind.MATCH_EXPR);
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
        assertThat(((EnumPatternNode) match.branches().get(0).pattern()).variantName()).isEqualTo("C");
        assertThat(((EnumPatternNode) match.branches().get(1).pattern()).variantName()).isEqualTo("A");
        assertThat(((EnumPatternNode) match.branches().get(2).pattern()).variantName()).isEqualTo("B");
    }

    @Test
    public void aBranchWithoutAnArrowIsRejected() {
        assertThat(parseFails("m.sol", """
                enum Color {
                    Red
                }
                func name(color: Color): String {
                    return match color {
                        Red "red"
                    }
                }
                """).hasErrors()).isTrue();
    }

    @Test
    public void aMatchWithoutAScrutineeIsRejected() {
        assertThat(parseFails("m.sol", """
                func name(): String {
                    return match {
                        _ => "x"
                    }
                }
                """).hasErrors()).isTrue();
    }

    @Test
    public void aMatchWithoutAClosingBraceIsRejected() {
        assertThat(parseFails("m.sol", """
                func name(): String {
                    return match 1 {
                        _ => "x"
                }
                """).hasErrors()).isTrue();
    }

    @Test
    public void aMatchBranchWithoutAPatternIsRejected() {
        assertThat(parseFails("m.sol", """
                func name(): String {
                    return match 1 {
                        => "x"
                    }
                }
                """).hasErrors()).isTrue();
    }

    @Test
    public void aTrailingCommaInsideAVariantPatternIsRejected() {
        assertThat(parseFails("m.sol", """
                enum Result {
                    Ok(Int)
                }
                func value(result: Result): Int {
                    return match result {
                        Ok(value,) => value
                    }
                }
                """).hasErrors()).isTrue();
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
            assertThat(branch.kind()).isEqualTo(AstKind.MATCH_BRANCH);
        }
    }
}
