/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.expression;

import java.util.List;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/** A parenthesized expression such as {@code (a + b)}. Parentheses are retained syntactically. */
public final class ParenExprNode extends ExpressionNode {

    private final ExpressionNode inner;

    public ParenExprNode(ExpressionNode inner, SourceSpan span) {
        super(AstKind.PAREN_EXPR, span);
        this.inner = inner;
    }

    public ExpressionNode inner() {
        return inner;
    }

    @Override
    public List<AstNode> children() {
        return List.of(inner);
    }
}
