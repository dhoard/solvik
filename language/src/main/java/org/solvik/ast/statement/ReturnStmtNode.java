/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.statement;

import java.util.List;
import java.util.Optional;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.source.SourceSpan;

/**
 * A {@code return;} or {@code return expression;} statement. Whether a bare return is legal
 * depends on the enclosing function's return type, which Phase 4 checks.
 */
public final class ReturnStmtNode extends StatementNode {

    private final ExpressionNode value;

    public ReturnStmtNode(ExpressionNode value, SourceSpan span) {
        super(AstKind.RETURN_STMT, span);
        this.value = value;
    }

    public Optional<ExpressionNode> value() {
        return Optional.ofNullable(value);
    }

    @Override
    public List<AstNode> children() {
        return value == null ? List.of() : List.of(value);
    }
}
