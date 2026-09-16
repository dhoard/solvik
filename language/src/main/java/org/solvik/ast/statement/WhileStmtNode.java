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

/** A pre-test {@code while (condition) block} loop. The condition must be {@code Boolean}. */
public final class WhileStmtNode extends StatementNode {

    private final ExpressionNode condition;
    private final BlockNode body;

    public WhileStmtNode(ExpressionNode condition, BlockNode body, SourceSpan span) {
        super(AstKind.WHILE_STMT, span);
        this.condition = Objects.requireNonNull(condition);
        this.body = Objects.requireNonNull(body);
    }

    public ExpressionNode condition() {
        return condition;
    }

    public BlockNode body() {
        return body;
    }

    @Override
    public List<AstNode> children() {
        return List.of(condition, body);
    }
}
