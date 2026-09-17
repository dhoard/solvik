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
 * A constant {@code switch} case label (docs/LANGUAGE_SPEC.md section 13), such as
 * {@code case 1:} or {@code case "a", "b":}. The static semantic pass, not the grammar, decides
 * whether the written expression is a compile-time constant and assignable to the switched value's
 * type.
 */
public final class ConstantCaseLabelNode extends CaseLabelNode {

    private final ExpressionNode expression;

    public ConstantCaseLabelNode(ExpressionNode expression, SourceSpan span) {
        super(AstKind.CASE_LABEL, span);
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
