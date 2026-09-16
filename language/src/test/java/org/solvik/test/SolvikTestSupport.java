/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import org.junit.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.ParameterNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.ast.expression.BinaryOperator;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.NameRefExprNode;
import org.solvik.ast.expression.ParenExprNode;
import org.solvik.ast.statement.BlockNode;
import org.solvik.ast.statement.ExprStmtNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.parser.SolvikParseResult;
import org.solvik.source.SourceFile;
import org.solvik.source.SourceSpan;

/** Shared helpers for Solvik Phase 1 parser tests. */
final class SolvikTestSupport {

    private SolvikTestSupport() {
    }

    static CompilationUnitNode parseOk(String name, String text) {
        SolvikParseResult result = org.solvik.parser.SolvikParser.parse(new SourceFile(name, text));
        if (!result.isSuccess()) {
            StringBuilder sb = new StringBuilder("unexpected parse errors:");
            result.diagnostics().all().forEach(d -> sb.append("\n  ").append(d));
            fail(sb.toString());
        }
        return result.requireAst();
    }

    static FunctionDeclNode onlyFunction(CompilationUnitNode cu) {
        assertEquals(1, cu.declarations().size());
        return (FunctionDeclNode) cu.declarations().get(0);
    }

    /** Asserts node kind and that its span slice of {@code source} is exactly {@code expected}. */
    static void assertNode(AstNode node, AstKind kind, String source, String expected) {
        assertEquals(kind, node.kind());
        int start = node.span().startOffset();
        int end = node.span().endOffset();
        assertTrue("span out of bounds: " + node.span(), end <= source.length());
        assertEquals(expected, source.substring(start, end));
    }

    static void assertSpan(AstNode node, AstKind kind, int start, int end) {
        assertEquals(kind, node.kind());
        assertEquals(SourceSpan.of(start, end), node.span());
    }

    /** Local declaration at a given index in a function body. */
    static LocalDeclNode local(FunctionDeclNode fn, int index) {
        return (LocalDeclNode) fn.body().statements().get(index);
    }

    static BlockNode body(FunctionDeclNode fn) {
        return fn.body();
    }

    static ReturnStmtNode ret(FunctionDeclNode fn, int index) {
        return (ReturnStmtNode) fn.body().statements().get(index);
    }

    static ExprStmtNode expr(FunctionDeclNode fn, int index) {
        return (ExprStmtNode) fn.body().statements().get(index);
    }

    static List<AstNode> kids(AstNode node) {
        return node.children();
    }

    static BinaryExprNode binary(AstNode node) {
        assertEquals(AstKind.BINARY_EXPR, node.kind());
        return (BinaryExprNode) node;
    }

    static CallExprNode call(AstNode node) {
        assertEquals(AstKind.CALL_EXPR, node.kind());
        return (CallExprNode) node;
    }

    static MemberAccessExprNode member(AstNode node) {
        assertEquals(AstKind.MEMBER_ACCESS_EXPR, node.kind());
        return (MemberAccessExprNode) node;
    }

    static ParenExprNode paren(AstNode node) {
        assertEquals(AstKind.PAREN_EXPR, node.kind());
        return (ParenExprNode) node;
    }

    static NameRefExprNode name(AstNode node) {
        assertEquals(AstKind.NAME_REF_EXPR, node.kind());
        return (NameRefExprNode) node;
    }

    static ParameterNode param(FunctionDeclNode fn, int index) {
        return fn.parameters().get(index);
    }

    /** Asserts the source fails to parse and exposes diagnostics but no AST. */
    static org.solvik.diagnostic.DiagnosticBag parseFails(String name, String text) {
        org.solvik.parser.SolvikParseResult result = org.solvik.parser.SolvikParser.parse(new SourceFile(name, text));
        if (result.isSuccess()) {
            fail("parse must fail: " + text);
        }
        assertTrue("failed parse must carry diagnostics", result.diagnostics().hasErrors());
        assertTrue("failed parse must expose no AST", result.ast().isEmpty());
        return result.diagnostics();
    }
}
