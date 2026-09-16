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

/** A bare identifier used as a value. Name resolution happens in a later phase. */
public final class NameRefExprNode extends ExpressionNode {

    private final String name;

    public NameRefExprNode(String name, SourceSpan span) {
        super(AstKind.NAME_REF_EXPR, span);
        this.name = Objects.requireNonNull(name);
    }

    public String name() {
        return name;
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}
