/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/** A call expression {@code callee(argument, ...)}; the callee is any expression. */
public final class CallExprNode extends ExpressionNode {

    private final ExpressionNode callee;
    private final List<ExpressionNode> arguments;

    public CallExprNode(ExpressionNode callee, List<ExpressionNode> arguments, SourceSpan span) {
        super(AstKind.CALL_EXPR, span);
        this.callee = Objects.requireNonNull(callee);
        this.arguments = List.copyOf(arguments);
    }

    public ExpressionNode callee() {
        return callee;
    }

    public List<ExpressionNode> arguments() {
        return arguments;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>();
        kids.add(callee);
        kids.addAll(arguments);
        return List.copyOf(kids);
    }
}
