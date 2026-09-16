/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.assertNode;
import static org.solvik.test.SolvikTestSupport.assertSpan;
import static org.solvik.test.SolvikTestSupport.binary;
import static org.solvik.test.SolvikTestSupport.call;
import static org.solvik.test.SolvikTestSupport.expr;
import static org.solvik.test.SolvikTestSupport.local;
import static org.solvik.test.SolvikTestSupport.member;
import static org.solvik.test.SolvikTestSupport.name;
import static org.solvik.test.SolvikTestSupport.onlyFunction;
import static org.solvik.test.SolvikTestSupport.paren;
import static org.solvik.test.SolvikTestSupport.parseOk;
import static org.solvik.test.SolvikTestSupport.ret;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.ast.expression.BinaryOperator;
import org.solvik.ast.expression.BoolLiteralNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.ParenExprNode;
import org.solvik.ast.expression.IntLiteralNode;
import org.solvik.ast.expression.StringLiteralNode;
import org.solvik.ast.statement.BindingKind;
import org.solvik.ast.statement.BlockNode;
import org.solvik.ast.statement.ElseBranchNode;
import org.solvik.ast.statement.IfStmtNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.source.SourceSpan;

/**
 * Positive parser tests: every supported construct with exact tree shape and exact source spans.
 * Inputs use explicit {@code ;} termination; newline termination and the newline/semicolon
 * equivalence properties are covered by {@link SolvikSemicolonInsertionTest} and
 * {@link SolvikSemicolonTokenStreamTest}.
 */
public final class SolvikParserTest {

    private static final String DOC = "fun add(a: Int, b: Int): Int {\n    return a + b;\n}\n";

    @Test
    public void docExampleShapeAndSpans() {
        CompilationUnitNode cu = parseOk("doc.sol", DOC);
        assertEquals(1, cu.declarations().size());
        // The compilation unit covers the whole text except the trailing newline.
        assertSpan(cu, AstKind.COMPILATION_UNIT, 0, DOC.length() - 1);

        FunctionDeclNode fn = onlyFunction(cu);
        assertNode(fn, AstKind.FUNCTION_DECL, DOC, DOC.substring(0, DOC.indexOf('}') + 1));
        assertEquals("add", fn.name());
        assertEquals("Int", fn.returnType().name());

        int aPos = DOC.indexOf("a: Int");
        assertNode(fn.parameters().get(0), AstKind.PARAMETER, DOC, "a: Int");
        assertNode(fn.parameters().get(1), AstKind.PARAMETER, DOC, "b: Int");
        assertSpan(fn.parameters().get(0).type(), AstKind.TYPE_REF, aPos + 3, aPos + 6);
        assertEquals("Int", fn.parameters().get(0).type().name());
        assertEquals("Int", fn.parameters().get(1).type().name());

        BlockNode body = fn.body();
        assertNode(body, AstKind.BLOCK, DOC, DOC.substring(DOC.indexOf('{'), DOC.indexOf('}') + 1));
        assertEquals(1, body.statements().size());

        ReturnStmtNode stmt = ret(fn, 0);
        assertNode(stmt, AstKind.RETURN_STMT, DOC, "return a + b;");
        assertTrue(stmt.value().isPresent());

        BinaryExprNode bin = binary(stmt.value().get());
        assertNode(bin, AstKind.BINARY_EXPR, DOC, "a + b");
        assertEquals(BinaryOperator.ADD, bin.operator());
        assertNode(bin.left(), AstKind.NAME_REF_EXPR, DOC, "a");
        assertNode(bin.right(), AstKind.NAME_REF_EXPR, DOC, "b");
        assertEquals("a", name(bin.left()).name());
        assertEquals("b", name(bin.right()).name());
    }

    @Test
    public void docExampleExactShapeTree() {
        CompilationUnitNode cu = parseOk("doc.sol", DOC);
        assertEquals("""
                COMPILATION_UNIT {
                  FUNCTION_DECL {
                    PARAMETER {
                      TYPE_REF
                    }
                    PARAMETER {
                      TYPE_REF
                    }
                    TYPE_REF
                    BLOCK {
                      RETURN_STMT {
                        BINARY_EXPR {
                          NAME_REF_EXPR
                          NAME_REF_EXPR
                        }
                      }
                    }
                  }
                }
                """, cu.shapeTree());
    }

    @Test
    public void literalsNamesAndStrings() {
        String src = "fun f(): Unit {\n" + //
                "  val a: Int = 42;\n" + //
                "  var b: Boolean = true;\n" + //
                "  val c = \"esc\\t\";\n" + //
                "}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("literals.sol", src));

        LocalDeclNode a = local(fn, 0);
        assertNode(a, AstKind.LOCAL_DECL, src, "val a: Int = 42;");
        assertEquals(BindingKind.VAL, a.bindingKind());
        assertEquals("a", a.name());
        assertEquals("Int", a.declaredType().orElseThrow().name());
        IntLiteralNode lit = (IntLiteralNode) a.initializer();
        assertNode(lit, AstKind.INT_LITERAL, src, "42");
        assertEquals("42", lit.lexeme());

        LocalDeclNode b = local(fn, 1);
        assertNode(b, AstKind.LOCAL_DECL, src, "var b: Boolean = true;");
        assertEquals(BindingKind.VAR, b.bindingKind());
        BoolLiteralNode bool = (BoolLiteralNode) b.initializer();
        assertNode(bool, AstKind.BOOL_LITERAL, src, "true");
        assertTrue(bool.value());

        LocalDeclNode c = local(fn, 2);
        assertNode(c, AstKind.LOCAL_DECL, src, "val c = \"esc\\t\";");
        assertTrue(c.declaredType().isEmpty());
        StringLiteralNode str = (StringLiteralNode) c.initializer();
        assertNode(str, AstKind.STRING_LITERAL, src, "\"esc\\t\"");
        assertEquals("\"esc\\t\"", str.lexeme());
    }

    @Test
    public void falseLiteralParsesAsBooleanFalse() {
        String src = "fun f(): Boolean {\n    return false;\n}\n";
        ReturnStmtNode r = ret(onlyFunction(parseOk("bool.sol", src)), 0);
        BoolLiteralNode bool = (BoolLiteralNode) r.value().get();
        assertEquals(AstKind.BOOL_LITERAL, bool.kind());
        assertFalse(bool.value());
    }

    @Test
    public void binaryPrecedenceAssociativityAndParentheses() {
        String src = "fun f(): Int {\n    val x = 1 - 2 - 3 * 4;\n    val y = (1 + 2) / 3;\n    return x;\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("prec.sol", src));

        BinaryExprNode top = binary(local(fn, 0).initializer());
        // Left associativity groups as ((1 - 2) - (3 * 4)).
        assertEquals(BinaryOperator.SUB, top.operator());
        BinaryExprNode left = binary(top.left());
        assertEquals(BinaryOperator.SUB, left.operator());
        assertEquals(AstKind.INT_LITERAL, left.left().kind());
        BinaryExprNode mul = binary(top.right());
        assertEquals(BinaryOperator.MUL, mul.operator());
        assertNode(top, AstKind.BINARY_EXPR, src, "1 - 2 - 3 * 4");
        assertNode(left, AstKind.BINARY_EXPR, src, "1 - 2");
        assertNode(mul, AstKind.BINARY_EXPR, src, "3 * 4");

        BinaryExprNode div = binary(local(fn, 1).initializer());
        assertEquals(BinaryOperator.DIV, div.operator());
        ParenExprNode p = paren(div.left());
        assertNode(p, AstKind.PAREN_EXPR, src, "(1 + 2)");
        assertNode(p.inner(), AstKind.BINARY_EXPR, src, "1 + 2");
    }

    @Test
    public void multiplicationBindsTighterAcrossMixedOperators() {
        String src = "fun f(a: Int, b: Int, c: Int): Int {\n    return a + b * c - a;\n}\n";
        ReturnStmtNode r = ret(onlyFunction(parseOk("mix.sol", src)), 0);
        BinaryExprNode top = binary(r.value().get());
        assertEquals(BinaryOperator.SUB, top.operator());
        BinaryExprNode add = binary(top.left());
        assertEquals(BinaryOperator.ADD, add.operator());
        assertEquals(BinaryOperator.MUL, binary(add.right()).operator());
        assertNode(add, AstKind.BINARY_EXPR, src, "a + b * c");
        assertNode(binary(add.right()), AstKind.BINARY_EXPR, src, "b * c");
    }

    @Test
    public void callsMemberAccessAndFoldingOrder() {
        String src = "fun f(): Unit {\n    obj.method(1)(2);\n    g(h.a.b(3), p + q.r);\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("calls.sol", src));

        CallExprNode outer = call(expr(fn, 0).expression());
        assertEquals(1, outer.arguments().size());
        CallExprNode inner = call(outer.callee());
        MemberAccessExprNode m = member(inner.callee());
        assertEquals("method", m.memberName());
        assertEquals("obj", name(m.receiver()).name());
        // Folded nodes start at the base primary and end at their own suffix.
        assertNode(m, AstKind.MEMBER_ACCESS_EXPR, src, "obj.method");
        assertNode(inner, AstKind.CALL_EXPR, src, "obj.method(1)");
        assertNode(outer, AstKind.CALL_EXPR, src, "obj.method(1)(2)");
        assertEquals(1, inner.arguments().size());

        CallExprNode call2 = call(expr(fn, 1).expression());
        assertEquals("g", name(call2.callee()).name());
        assertEquals(2, call2.arguments().size());
        CallExprNode hab = call(call2.arguments().get(0));
        assertNode(hab, AstKind.CALL_EXPR, src, "h.a.b(3)");
        assertNode(member(hab.callee()), AstKind.MEMBER_ACCESS_EXPR, src, "h.a.b");
        assertEquals(1, hab.arguments().size());
        BinaryExprNode plus = binary(call2.arguments().get(1));
        assertEquals(BinaryOperator.ADD, plus.operator());
        assertNode(member(plus.right()), AstKind.MEMBER_ACCESS_EXPR, src, "q.r");
    }

    @Test
    public void callOnParenthesizedExpression() {
        String src = "fun f(a: Int, b: Int, c: Int): Unit {\n    (a + b).c(1);\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("parencall.sol", src));
        CallExprNode c = call(expr(fn, 0).expression());
        MemberAccessExprNode m = member(c.callee());
        ParenExprNode p = paren(m.receiver());
        assertNode(p, AstKind.PAREN_EXPR, src, "(a + b)");
        assertEquals("c", m.memberName());
        assertNode(c, AstKind.CALL_EXPR, src, "(a + b).c(1)");
    }

    @Test
    public void zeroArgumentCallsAreDistinctFromMemberReads() {
        String src = "fun f(): Unit {\n    obj.load();\n    obj.field;\n    plain();\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("noargs.sol", src));
        assertNode(expr(fn, 0).expression(), AstKind.CALL_EXPR, src, "obj.load()");
        assertNode(expr(fn, 1).expression(), AstKind.MEMBER_ACCESS_EXPR, src, "obj.field");
        assertNode(expr(fn, 2).expression(), AstKind.CALL_EXPR, src, "plain()");
        assertEquals(0, call(expr(fn, 2).expression()).arguments().size());
    }

    @Test
    public void nestedParenthesesPreserveDepth() {
        String src = "fun f(a: Int): Int {\n    return ((a));\n}\n";
        ReturnStmtNode r = ret(onlyFunction(parseOk("nestparen.sol", src)), 0);
        ParenExprNode outer = paren(r.value().get());
        assertNode(outer, AstKind.PAREN_EXPR, src, "((a))");
        assertNode(paren(outer.inner()), AstKind.PAREN_EXPR, src, "(a)");
        assertNode(paren(outer.inner()).inner(), AstKind.NAME_REF_EXPR, src, "a");
    }

    @Test
    public void ifElseIfElseStructure() {
        String src = "fun f(c: Boolean): Unit {\n" + //
                "    if (c) {\n        return;\n    } else if (c) {\n        return;\n    } else {\n        return;\n    }\n" + //
                "}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("if.sol", src));
        IfStmtNode if1 = (IfStmtNode) fn.body().statements().get(0);
        assertNode(if1.condition(), AstKind.NAME_REF_EXPR, src, "c");
        ElseBranchNode else1 = if1.elseBranch().orElseThrow();
        assertTrue(else1.isChainedIf());
        assertNode(else1, AstKind.ELSE_BRANCH, src, "else if (c) {\n        return;\n    } else {\n        return;\n    }");
        IfStmtNode if2 = else1.chainedIf().orElseThrow();
        ElseBranchNode else2 = if2.elseBranch().orElseThrow();
        assertFalse(else2.isChainedIf());
        assertNode(else2.block().orElseThrow(), AstKind.BLOCK, src, "{\n        return;\n    }");
        assertNode(if1.thenBlock(), AstKind.BLOCK, src, "{\n        return;\n    }");
    }

    @Test
    public void ifWithoutElseHasNoBranch() {
        String src = "fun f(c: Boolean): Unit {\n    if (c) {\n        g();\n    }\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("if.sol", src));
        IfStmtNode if1 = (IfStmtNode) fn.body().statements().get(0);
        assertTrue(if1.elseBranch().isEmpty());
        List<AstNode> kids = if1.children();
        assertEquals(2, kids.size());
        assertEquals(AstKind.BLOCK, kids.get(1).kind());
    }

    @Test
    public void blocksMayBeEmpty() {
        String src = "fun f(): Unit {\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("emptybody.sol", src));
        assertEquals(0, fn.body().statements().size());
        assertEquals(0, fn.parameters().size());
        assertNode(fn.body(), AstKind.BLOCK, src, "{\n}");
    }

    @Test
    public void bareReturnHasNoValue() {
        String src = "fun f(): Unit {\n    return;\n}\n";
        ReturnStmtNode r = ret(onlyFunction(parseOk("bare.sol", src)), 0);
        assertTrue(r.value().isEmpty());
        assertEquals(List.of(), r.children());
        assertNode(r, AstKind.RETURN_STMT, src, "return;");
    }

    @Test
    public void multipleFunctionsPreserveOrderAndSpans() {
        String src = "fun a(): Unit {\n}\nfun b(x: Int): Int {\n    return x;\n}\n";
        CompilationUnitNode cu = parseOk("two.sol", src);
        assertEquals(2, cu.declarations().size());
        assertEquals("a", ((FunctionDeclNode) cu.declarations().get(0)).name());
        assertEquals("b", ((FunctionDeclNode) cu.declarations().get(1)).name());
        assertSpan(cu.declarations().get(0), AstKind.FUNCTION_DECL, 0, src.indexOf('}') + 1);
        assertSpan(cu.declarations().get(1), AstKind.FUNCTION_DECL, src.indexOf("fun b"), src.lastIndexOf('}') + 1);
    }

    @Test
    public void emptySourceParsesToEmptyUnit() {
        CompilationUnitNode cu = parseOk("empty.sol", "");
        assertEquals(List.of(), cu.declarations());
        assertEquals(SourceSpan.of(0, 0), cu.span());
    }

    /** Structural invariant: every node span is contained in its parent span. */
    @Test
    public void spansAreNestedWithinParents() {
        String src = "fun f(x: Int): Int {\n" + //
                "    val y: Int = (x + g(x, obj.f(1))) * 2;\n" + //
                "    obj.a.b(1).c;\n" + //
                "    if (true) {\n        return y;\n    } else {\n        return x;\n    }\n" + //
                "}\n";
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parseOk("nest.sol", src));
        while (!stack.isEmpty()) {
            AstNode node = stack.pop();
            for (AstNode child : node.children()) {
                assertTrue(child.kind() + " " + child.span() + " outside " + node.kind() + " " + node.span(), node.span().contains(child.span()));
                stack.push(child);
            }
        }
    }

    /** Structural invariant: children follow source order without overlap. */
    @Test
    public void siblingsFollowSourceOrder() {
        String src = "fun f(): Unit {\n    val v = h(1, 2, obj.x);\n    return v;\n}\n";
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parseOk("order.sol", src));
        while (!stack.isEmpty()) {
            AstNode node = stack.pop();
            AstNode previous = null;
            for (AstNode child : node.children()) {
                if (previous != null) {
                    assertTrue("out of order: " + previous.span() + " then " + child.span(), previous.span().startOffset() <= child.span().startOffset());
                    assertTrue("overlap: " + previous.span() + " " + child.span(), !previous.span().overlaps(child.span()));
                }
                previous = child;
                stack.push(child);
            }
        }
    }

    @Test
    public void childCollectionsAreUnmodifiable() {
        CompilationUnitNode cu = parseOk("imm.sol", DOC);
        assertThrows(UnsupportedOperationException.class, () -> cu.children().add(null));
        assertThrows(UnsupportedOperationException.class, () -> cu.declarations().add(null));
        FunctionDeclNode fn = onlyFunction(cu);
        assertThrows(UnsupportedOperationException.class, () -> fn.parameters().clear());
        assertThrows(UnsupportedOperationException.class, () -> fn.body().statements().clear());
    }

    /** Re-parsing identical sources yields structurally identical trees. */
    @Test
    public void parsingIsDeterministic() {
        String first = parseOk("d1.sol", DOC).shapeTree();
        String second = parseOk("d2.sol", DOC).shapeTree();
        assertEquals(first, second);
    }
}
