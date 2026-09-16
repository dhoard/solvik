/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.expression;

import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/** A prefix unary expression such as {@code -x} or {@code !flag}. */
public final class UnaryExprNode extends ExpressionNode {

    private final UnaryOperator operator;
    private final ExpressionNode operand;

    public UnaryExprNode(UnaryOperator operator, ExpressionNode operand, SourceSpan span) {
        super(AstKind.UNARY_EXPR, span);
        this.operator = Objects.requireNonNull(operator);
        this.operand = Objects.requireNonNull(operand);
    }

    public UnaryOperator operator() {
        return operator;
    }

    public ExpressionNode operand() {
        return operand;
    }

    @Override
    public List<AstNode> children() {
        return List.of(operand);
    }
}
