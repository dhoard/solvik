/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.source.SourceSpan;

/**
 * A three-clause {@code for (init; condition; update) block} loop. Every clause is optional in the
 * grammar; the semantic pass enforces that the initializer is a local declaration or an assignment
 * and that the update clause is an assignment.
 */
public final class ForStmtNode extends StatementNode {

    private final StatementNode initializer;
    private final ExpressionNode condition;
    private final StatementNode update;
    private final BlockNode body;

    public ForStmtNode(StatementNode initializer, ExpressionNode condition, StatementNode update, BlockNode body, SourceSpan span) {
        super(AstKind.FOR_STMT, span);
        this.initializer = initializer;
        this.condition = condition;
        this.update = update;
        this.body = Objects.requireNonNull(body);
    }

    public Optional<StatementNode> initializer() {
        return Optional.ofNullable(initializer);
    }

    public Optional<ExpressionNode> condition() {
        return Optional.ofNullable(condition);
    }

    public Optional<StatementNode> update() {
        return Optional.ofNullable(update);
    }

    public BlockNode body() {
        return body;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>(4);
        if (initializer != null) {
            kids.add(initializer);
        }
        if (condition != null) {
            kids.add(condition);
        }
        if (update != null) {
            kids.add(update);
        }
        kids.add(body);
        return List.copyOf(kids);
    }
}
