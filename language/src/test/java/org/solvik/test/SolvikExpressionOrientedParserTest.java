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
import org.solvik.ast.AstKind;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.BlockExprNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.ast.expression.IfExprNode;
import org.solvik.ast.expression.IntegerLiteralNode;
import org.solvik.ast.expression.MatchExprNode;
import org.solvik.ast.expression.SwitchExprNode;
import org.solvik.ast.statement.BlockNode;
import org.solvik.ast.statement.ExprStmtNode;
import org.solvik.ast.statement.IfStmtNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.ast.statement.SwitchStmtNode;

/**
 * Parser and AST tests for expression-oriented constructs (docs/LANGUAGE_SPEC.md section 21): block,
 * {@code if}, and {@code switch} expressions, their structural difference from the statement forms,
 * tail selection, and complete source spans.
 */
public final class SolvikExpressionOrientedParserTest {

    private static LocalDeclNode firstLocal(CompilationUnitNode unit, String functionName) {
        for (var declaration : unit.declarations()) {
            if (declaration instanceof FunctionDeclNode function && function.name().equals(functionName)) {
                return (LocalDeclNode) function.body().statements().get(0);
            }
        }
        throw new AssertionError("no function named " + functionName);
    }

    private static ExpressionNode initializer(CompilationUnitNode unit, String functionName) {
        return firstLocal(unit, functionName).initializer();
    }

    @Test
    public void blockExpressionInitializerBuildsBlockExprNodeWithTail() {
        CompilationUnitNode unit = parseOk("b.sol", """
                func f(): Integer {
                    var x: Integer = {
                        var base: Integer = 20
                        base + 22
                    }
                    return x
                }

                """);
        assertThat(initializer(unit, "f").kind()).isEqualTo(AstKind.BLOCK_EXPR);
        BlockExprNode block = (BlockExprNode) initializer(unit, "f");
        assertThat(block.body().statements().size()).isEqualTo(1);
        assertThat(block.body().tail()).isPresent();
    }

    @Test
    public void standaloneScopeBlockRemainsAStatementBlock() {
        CompilationUnitNode unit = parseOk("b.sol", """
                func f() {
                    {
                        var local: Integer = 1
                        print(local)
                    }
                }

                """);
        BlockNode block = (BlockNode) ((FunctionDeclNode) unit.declarations().get(0)).body().statements().get(0);
        assertThat(block.kind()).isEqualTo(AstKind.BLOCK);
        assertThat(block.tail()).isEmpty();
    }

    @Test
    public void explicitSynthesizedAndAbsentTailSemicolonsAgree() {
        String inlineFunction = """
                func f(): Integer {
                    var x: Integer = {
                        42
                    }
                    return x
                }

                """;
        String explicitFunction = """
                func f(): Integer {
                    var x: Integer = {
                        42
                    }
                    return x
                }

                """;
        String newlineFunction = """
                func f(): Integer {
                    var x: Integer = {
                        42
                    }
                    return x
                }

                """;
        BlockNode inline = ((BlockExprNode) initializer(parseOk("a.sol", inlineFunction), "f")).body();
        BlockNode explicit = ((BlockExprNode) initializer(parseOk("b.sol", explicitFunction), "f")).body();
        BlockNode newline = ((BlockExprNode) initializer(parseOk("c.sol", newlineFunction), "f")).body();
        assertThat(inline.statements()).isEmpty();
        assertThat(explicit.statements()).isEmpty();
        assertThat(newline.statements()).isEmpty();
        assertThat(((IntegerLiteralNode) inline.tail().orElseThrow()).lexeme()).isEqualTo("42");
        assertThat(((IntegerLiteralNode) explicit.tail().orElseThrow()).lexeme()).isEqualTo("42");
        assertThat(((IntegerLiteralNode) newline.tail().orElseThrow()).lexeme()).isEqualTo("42");
    }

    @Test
    public void ifExpressionAndStatementHaveDistinctKinds() {
        CompilationUnitNode expression = parseOk("e.sol", """
                func f(flag: Boolean): Integer {
                    var x: Integer = if (flag) {
                        1
                    }
                    else {
                        2
                    }
                    return x
                }

                """);
        assertThat(initializer(expression, "f").kind()).isEqualTo(AstKind.IF_EXPR);
        CompilationUnitNode statement = parseOk("s.sol", """
                func f(flag: Boolean) {
                    if (flag) {
                        print("yes")
                    }
                    else {
                        print("no")
                    }
                }

                """);
        assertThat(((FunctionDeclNode) statement.declarations().get(0)).body().statements().get(0).kind())
                .isEqualTo(AstKind.IF_STMT);
    }

    @Test
    public void elseIfChainIsNestedExpression() {
        CompilationUnitNode unit = parseOk("e.sol", """
                func f(value: Integer): String {
                    var label: String = if (value < 0) {
                        "negative"
                    }
                    else if (value == 0) {
                        "zero"
                    }
                    else {
                        "positive"
                    }
                    return label
                }

                """);
        IfExprNode outer = (IfExprNode) initializer(unit, "f");
        IfExprNode chained = (IfExprNode) outer.elseValue().orElseThrow();
        assertThat(chained.kind()).isEqualTo(AstKind.IF_EXPR);
        assertThat(chained.elseValue()).isPresent();
    }

    @Test
    public void switchExpressionAndStatementHaveDistinctKinds() {
        CompilationUnitNode expression = parseOk("e.sol", """
                func f(value: Integer): String {
                    var label: String = switch (value) {
                        case 1 {
                            "one"
                        }
                        default {
                            "other"
                        }
                    }
                    return label
                }

                """);
        assertThat(initializer(expression, "f").kind()).isEqualTo(AstKind.SWITCH_EXPR);
        assertThat(((SwitchExprNode) initializer(expression, "f")).cases().size()).isEqualTo(2);
        CompilationUnitNode statement = parseOk("s.sol", """
                func f(value: Integer) {
                    switch (value) {
                        case 1 {
                            print("one")
                        }
                        default {
                            print("other")
                        }
                    }
                }

                """);
        assertThat(((FunctionDeclNode) statement.declarations().get(0)).body().statements().get(0).kind())
                .isEqualTo(AstKind.SWITCH_STMT);
        assertThat(((SwitchStmtNode) ((FunctionDeclNode) statement.declarations().get(0)).body().statements().get(0)).cases().size())
                .isEqualTo(2);
    }

    @Test
    public void matchBranchAcceptsABlockExpression() {
        CompilationUnitNode unit = parseOk("m.sol", """
                enum Result {
                    Ok(Integer)
                    Error(String)
                }
                func describe(result: Result): String {
                    return match result {
                        Ok(value) => {
                            print("ok")
                            "value"
                        }
                        Error(message) => message
                    }
                }
                """);
        ReturnStmtNode ret = (ReturnStmtNode) ((FunctionDeclNode) unit.declarations().get(1)).body().statements().get(0);
        MatchExprNode match = (MatchExprNode) ret.value().orElseThrow();
        assertThat(match.branches().get(0).result().kind()).isEqualTo(AstKind.BLOCK_EXPR);
    }

    @Test
    public void blockExpressionCarriesCompleteSpan() {
        String source = """
                func f(): Integer {
                    var x: Integer = {
                        42
                    }
                    return x
                }

                """;
        CompilationUnitNode unit = parseOk("span.sol", source);
        BlockExprNode block = (BlockExprNode) initializer(unit, "f");
        int open = source.indexOf("var x: Integer = {") + "var x: Integer = ".length();
        int close = source.indexOf('}', open);
        assertThat(source.substring(block.span().startOffset(), block.span().endOffset()))
                .isEqualTo(source.substring(open, close + 1));
    }

    @Test
    public void expressionIfInsideCallAndReturnArgument() {
        CompilationUnitNode unit = parseOk("c.sol", """
                func f(flag: Boolean): String {
                    print(if (flag) {
                        "yes"
                    }
                    else {
                        "no"
                    }
                    )
                    return if (flag) {
                        "Y"
                    }
                    else {
                        "N"
                    }
                }
                """);
        FunctionDeclNode function = (FunctionDeclNode) unit.declarations().get(0);
        ExprStmtNode callStatement = (ExprStmtNode) function.body().statements().get(0);
        assertThat(callStatement.expression().kind()).isEqualTo(AstKind.CALL_EXPR);
        ReturnStmtNode ret = (ReturnStmtNode) function.body().statements().get(1);
        assertThat(ret.value().orElseThrow().kind()).isEqualTo(AstKind.IF_EXPR);
    }

    @Test
    public void newNodesExposeStructuralChildren() {
        CompilationUnitNode unit = parseOk("children.sol", """
                func f(flag: Boolean): Integer {
                    var a: Integer = {
                        1
                    }
                    var b: Integer = if (flag) {
                        1
                    }
                    else {
                        2
                    }
                    var c: Integer = if (flag) {
                        1
                    }
                    var d: Integer = switch (1) {
                        case 1 {
                            1
                        }
                        default {
                            2
                        }
                    }
                    return 0
                }

                """);
        FunctionDeclNode function = (FunctionDeclNode) unit.declarations().get(0);
        BlockExprNode a = (BlockExprNode) ((LocalDeclNode) function.body().statements().get(0)).initializer();
        IfExprNode b = (IfExprNode) ((LocalDeclNode) function.body().statements().get(1)).initializer();
        IfExprNode c = (IfExprNode) ((LocalDeclNode) function.body().statements().get(2)).initializer();
        SwitchExprNode d = (SwitchExprNode) ((LocalDeclNode) function.body().statements().get(3)).initializer();
        // A block expression owns its body; a value body exposes the tail as a structural child.
        assertThat(a.children()).hasSize(1);
        assertThat(a.body().children()).hasSize(1);
        // An if expression exposes condition, then, and else only when one is written.
        assertThat(b.children()).hasSize(3);
        assertThat(c.children()).hasSize(2);
        // A switch expression exposes the scrutinee followed by every case in source order.
        assertThat(d.children()).hasSize(3);
        assertThat(a.shapeTree()).contains("BLOCK_EXPR").contains("BLOCK").contains("INTEGER_LITERAL");
    }
}
