/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.statement;

import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.source.SourceSpan;

/**
 * An expression statement: the only value-producing expression permitted as a statement is a call
 * (per the language specification); enforcing that restriction belongs to semantic validation in
 * Phase 4, so the syntax parser records any expression here.
 */
public final class ExprStmtNode extends StatementNode {

    private final ExpressionNode expression;

    public ExprStmtNode(ExpressionNode expression, SourceSpan span) {
        super(AstKind.EXPR_STMT, span);
        this.expression = Objects.requireNonNull(expression);
    }

    public ExpressionNode expression() {
        return expression;
    }

    @Override
    public List<AstNode> children() {
        return List.of(expression);
    }
}
