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
 * The {@code super} expression (docs/LANGUAGE_SPEC.md section 7). It may appear only as the callee
 * of a constructor's {@code super(arguments)} statement or as the receiver of a
 * {@code super.member} access; using {@code super} as a value or outside a class is a static
 * diagnostic.
 */
public final class SuperExprNode extends ExpressionNode {

    public SuperExprNode(SourceSpan span) {
        super(AstKind.SUPER_EXPR, span);
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}
