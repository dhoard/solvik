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
import static org.solvik.test.SolvikTestSupport.assertNode;
import static org.solvik.test.SolvikTestSupport.body;
import static org.solvik.test.SolvikTestSupport.local;
import static org.solvik.test.SolvikTestSupport.onlyFunction;
import static org.solvik.test.SolvikTestSupport.parseOk;
import static org.solvik.test.SolvikTestSupport.ret;

import org.junit.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.ast.expression.BinaryOperator;
import org.solvik.ast.expression.UnaryExprNode;
import org.solvik.ast.expression.UnaryOperator;
import org.solvik.ast.statement.AssignStmtNode;
import org.solvik.ast.statement.BreakStmtNode;
import org.solvik.ast.statement.ContinueStmtNode;
import org.solvik.ast.statement.ForStmtNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.WhileStmtNode;

/**
 * Phase 4 parser tests: assignment statements, {@code while}, three-clause {@code for},
 * {@code break}/{@code continue}, and the unary/ordering/equality/logical operator grammar. The
 * semantic consequences of these constructs are covered by the semantic test classes.
 */
public final class SolvikControlFlowParserTest {

    @Test
    public void assignmentStatementShapeAndSpan() {
        String src = "func f(): Int {\n    var x = 1;\n    x = x + 1;\n    return x;\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("assign.sol", src));
        AssignStmtNode assign = (AssignStmtNode) body(fn).statements().get(1);
        assertNode(assign, AstKind.ASSIGN_STMT, src, "x = x + 1;");
        assertEquals("x", SolvikTestSupport.name(assign.target()).name());
        assertEquals(AstKind.BINARY_EXPR, assign.value().kind());
    }

    @Test
    public void whileLoopShapeAndBody() {
        String src = "func f(c: Boolean): Unit {\n    while (c) {\n        break;\n    }\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("while.sol", src));
        WhileStmtNode loop = (WhileStmtNode) body(fn).statements().get(0);
        assertNode(loop, AstKind.WHILE_STMT, src, "while (c) {\n        break;\n    }");
        assertEquals(AstKind.NAME_REF_EXPR, loop.condition().kind());
        assertNode(loop.body().statements().get(0), AstKind.BREAK_STMT, src, "break;");
    }

    @Test
    public void forLoopShapeWithAllClauses() {
        String src = "func f(limit: Int): Int {\n    var total = 0;\n    for (var i = 0; i < limit; i = i + 1) {\n        total = total + i;\n    }\n    return total;\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("for.sol", src));
        ForStmtNode loop = (ForStmtNode) body(fn).statements().get(1);
        assertNode(loop, AstKind.FOR_STMT, src, //
                "for (var i = 0; i < limit; i = i + 1) {\n        total = total + i;\n    }");
        LocalDeclNode initializer = (LocalDeclNode) loop.initializer().orElseThrow();
        assertNode(initializer, AstKind.LOCAL_DECL, src, "var i = 0");
        assertEquals("i", initializer.name());
        assertNode(loop.condition().orElseThrow(), AstKind.BINARY_EXPR, src, "i < limit");
        AssignStmtNode update = (AssignStmtNode) loop.update().orElseThrow();
        assertNode(update, AstKind.ASSIGN_STMT, src, "i = i + 1");
        assertNode(loop.body().statements().get(0), AstKind.ASSIGN_STMT, src, "total = total + i;");
    }

    @Test
    public void forLoopAllClausesMayBeOmitted() {
        String src = "func f(): Unit {\n    for (;;) {\n        break;\n    }\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("forempty.sol", src));
        ForStmtNode loop = (ForStmtNode) body(fn).statements().get(0);
        assertTrue(loop.initializer().isEmpty());
        assertTrue(loop.condition().isEmpty());
        assertTrue(loop.update().isEmpty());
        assertNode(loop, AstKind.FOR_STMT, src, "for (;;) {\n        break;\n    }");
    }

    @Test
    public void forLoopAcceptsAnAssignmentInitializer() {
        String src = "func f(): Unit {\n    var i = 0;\n    for (i = 0; ; ) {\n        continue;\n    }\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("forassign.sol", src));
        ForStmtNode loop = (ForStmtNode) body(fn).statements().get(1);
        assertNode(loop.initializer().orElseThrow(), AstKind.ASSIGN_STMT, src, "i = 0");
        assertNode(loop.body().statements().get(0), AstKind.CONTINUE_STMT, src, "continue;");
    }

    @Test
    public void breakAndContinueStatementsStandAlone() {
        String src = "func f(c: Boolean): Unit {\n    while (c) {\n        if (c) {\n            break;\n        }\n        continue;\n    }\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("loopctl.sol", src));
        WhileStmtNode loop = (WhileStmtNode) body(fn).statements().get(0);
        BreakStmtNode brk = (BreakStmtNode) loop.body().statements().get(0).children().get(1).children().get(0);
        assertNode(brk, AstKind.BREAK_STMT, src, "break;");
        assertNode(loop.body().statements().get(1), AstKind.CONTINUE_STMT, src, "continue;");
    }

    @Test
    public void unaryAndBinaryPrecedenceIsStructural() {
        String src = "func f(a: Int, b: Int, c: Boolean): Boolean {\n    return a < b && c || !c;\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("prec.sol", src));
        BinaryExprNode or = (BinaryExprNode) ret(fn, 0).value().orElseThrow();
        assertEquals(BinaryOperator.OR, or.operator());
        BinaryExprNode and = (BinaryExprNode) or.left();
        assertEquals(BinaryOperator.AND, and.operator());
        BinaryExprNode less = (BinaryExprNode) and.left();
        assertEquals(BinaryOperator.LT, less.operator());
        UnaryExprNode not = (UnaryExprNode) or.right();
        assertEquals(UnaryOperator.NOT, not.operator());
        assertNode(or, AstKind.BINARY_EXPR, src, "a < b && c || !c");
        assertNode(not, AstKind.UNARY_EXPR, src, "!c");
    }

    @Test
    public void unaryMinusBindsTighterThanMultiplication() {
        String src = "func f(a: Int, b: Int): Int {\n    return -a * b;\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("neg.sol", src));
        BinaryExprNode mul = (BinaryExprNode) ret(fn, 0).value().orElseThrow();
        assertEquals(BinaryOperator.MUL, mul.operator());
        UnaryExprNode neg = (UnaryExprNode) mul.left();
        assertEquals(UnaryOperator.NEGATE, neg.operator());
        assertNode(neg, AstKind.UNARY_EXPR, src, "-a");
    }

    @Test
    public void equalityAndRelationalOperatorsAreStructural() {
        String src = "func f(a: Int, b: Int): Boolean {\n    return a == b || a != b && a <= b || a >= b;\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("eq.sol", src));
        // && binds tighter than ||, so the tree nests left-associatively per grammar rule.
        BinaryExprNode top = (BinaryExprNode) ret(fn, 0).value().orElseThrow();
        assertEquals(BinaryOperator.OR, top.operator());
        assertNode(top, AstKind.BINARY_EXPR, src, "a == b || a != b && a <= b || a >= b");
    }

    @Test
    public void statementListKeepsDeclarationsLoopsAssignmentsAndReturnsOrdered() {
        String src = "func f(n: Int): Int {\n    var total = 0;\n    while (n > 0) {\n        total = total + n;\n        n = n - 1;\n    }\n    return total;\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("mixed.sol", src));
        assertEquals(3, body(fn).statements().size());
        assertEquals(AstKind.LOCAL_DECL, body(fn).statements().get(0).kind());
        assertEquals(AstKind.WHILE_STMT, body(fn).statements().get(1).kind());
        assertEquals(AstKind.RETURN_STMT, body(fn).statements().get(2).kind());
        assertNode(local(fn, 0), AstKind.LOCAL_DECL, src, "var total = 0;");
    }
}
