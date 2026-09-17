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
import org.solvik.ast.declaration.TypeRefNode;
import org.solvik.source.SourceSpan;

/**
 * A checked cast {@code value as T} (docs/LANGUAGE_SPEC.md section 18). The cast expression has type
 * {@code T}; an unsuccessful cast raises a Solvik runtime type error rather than evaluating to
 * {@code null}. The operand and the written target type are children.
 */
public final class CastExprNode extends ExpressionNode {

    private final ExpressionNode operand;
    private final TypeRefNode typeRef;

    public CastExprNode(ExpressionNode operand, TypeRefNode typeRef, SourceSpan span) {
        super(AstKind.CAST_EXPR, span);
        this.operand = Objects.requireNonNull(operand);
        this.typeRef = Objects.requireNonNull(typeRef);
    }

    public ExpressionNode operand() {
        return operand;
    }

    public TypeRefNode typeRef() {
        return typeRef;
    }

    @Override
    public List<AstNode> children() {
        return List.of(operand, typeRef);
    }
}
