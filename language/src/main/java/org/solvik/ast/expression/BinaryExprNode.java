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

/**
 * A left-associative binary expression such as {@code a + b}. The grammar encodes precedence
 * directly (multiplicative binds tighter than additive) and never produces chains requiring
 * runtime precedence resolution.
 */
public final class BinaryExprNode extends ExpressionNode {

    private final BinaryOperator operator;
    private final ExpressionNode left;
    private final ExpressionNode right;

    public BinaryExprNode(BinaryOperator operator, ExpressionNode left, ExpressionNode right, SourceSpan span) {
        super(AstKind.BINARY_EXPR, span);
        this.operator = Objects.requireNonNull(operator);
        this.left = Objects.requireNonNull(left);
        this.right = Objects.requireNonNull(right);
    }

    public BinaryOperator operator() {
        return operator;
    }

    public ExpressionNode left() {
        return left;
    }

    public ExpressionNode right() {
        return right;
    }

    @Override
    public List<AstNode> children() {
        return List.of(left, right);
    }
}
