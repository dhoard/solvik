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
 * An assignment statement {@code target = value}. Assignment is a statement, never a
 * value-producing expression. The target is recorded as an expression so the semantic pass can
 * validate that it denotes a mutable local.
 */
public final class AssignStmtNode extends StatementNode {

    private final ExpressionNode target;
    private final ExpressionNode value;

    public AssignStmtNode(ExpressionNode target, ExpressionNode value, SourceSpan span) {
        super(AstKind.ASSIGN_STMT, span);
        this.target = Objects.requireNonNull(target);
        this.value = Objects.requireNonNull(value);
    }

    public ExpressionNode target() {
        return target;
    }

    public ExpressionNode value() {
        return value;
    }

    @Override
    public List<AstNode> children() {
        return List.of(target, value);
    }
}
