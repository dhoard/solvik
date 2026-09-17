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
import static org.solvik.test.SolvikTestSupport.assertNode;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.statement.StatementNode;

/**
 * Positive parser tests for executable top-level statements and the implicit `main` they form
 * (docs/LANGUAGE_SPEC.md section 6). A file's declarations stay in {@code declarations()} and its
 * top-level statements stay in {@code statements()}; both kinds share one source-ordered
 * {@code children()} list so traversal does not depend on the split.
 */
public final class SolvikImplicitMainParserTest {

    @Test
    public void bareStatementsParseAsTheImplicitMainBody() {
        String src = "println(\"hi\")\n";
        CompilationUnitNode cu = parseOk("bare.sol", src);
        assertEquals(List.of(), cu.declarations());
        assertTrue(cu.hasImplicitMain());
        assertEquals(1, cu.statements().size());
        assertNode(cu.statements().get(0), AstKind.EXPR_STMT, src, "println(\"hi\")");
    }

    @Test
    public void aTopLevelLocalIsAnOrdinaryLocalStatement() {
        String src = "val x: Int = 1\n";
        CompilationUnitNode cu = parseOk("local.sol", src);
        assertEquals(1, cu.statements().size());
        assertNode(cu.statements().get(0), AstKind.LOCAL_DECL, src, "val x: Int = 1");
    }

    @Test
    public void topLevelControlFlowStatementsParse() {
        String src = "if (true) {\n    return\n}\nwhile (false) {\n    break\n}\n";
        CompilationUnitNode cu = parseOk("control.sol", src);
        List<StatementNode> statements = cu.statements();
        assertEquals(2, statements.size());
        assertEquals(AstKind.IF_STMT, statements.get(0).kind());
        assertEquals(AstKind.WHILE_STMT, statements.get(1).kind());
    }

    @Test
    public void declarationsAndStatementsInterleaveInSourceOrder() {
        String src = "helper()\nfunc helper(): Unit {\n    println(\"x\")\n}\nhelper()\n";
        CompilationUnitNode cu = parseOk("mixed.sol", src);
        assertEquals(1, cu.declarations().size());
        assertEquals("helper", ((FunctionDeclNode) cu.declarations().get(0)).name());
        assertEquals(2, cu.statements().size());
        List<AstNode> kids = cu.children();
        assertEquals(3, kids.size());
        assertEquals(AstKind.EXPR_STMT, kids.get(0).kind());
        assertEquals(AstKind.FUNCTION_DECL, kids.get(1).kind());
        assertEquals(AstKind.EXPR_STMT, kids.get(2).kind());
        assertTrue(kids.get(0).span().startOffset() < kids.get(1).span().startOffset());
        assertTrue(kids.get(1).span().startOffset() < kids.get(2).span().startOffset());
    }

    @Test
    public void aDeclarationOnlyFileHasNoImplicitMain() {
        CompilationUnitNode cu = parseOk("decl.sol", "func f(): Unit {\n}\n");
        assertEquals(1, cu.declarations().size());
        assertFalse(cu.hasImplicitMain());
        assertEquals(List.of(), cu.statements());
    }

    @Test
    public void topLevelStatementsWithoutSemicolonInsertionBoundariesStillParse() {
        String src = "val a = 1; val b = 2; println(a + b);\n";
        CompilationUnitNode cu = parseOk("explicit.sol", src);
        assertEquals(3, cu.statements().size());
    }
}
