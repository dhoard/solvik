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

/**
 * The {@code this} expression (docs/LANGUAGE_SPEC.md section 7). It is valid only inside an
 * instance method or constructor; its static type is the enclosing class type.
 */
public final class ThisExprNode extends ExpressionNode {

    public ThisExprNode(SourceSpan span) {
        super(AstKind.THIS_EXPR, span);
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}
