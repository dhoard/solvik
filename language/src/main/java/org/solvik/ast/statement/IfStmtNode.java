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

/** An {@code if (condition) block [else ...]} statement. Conditions must be {@code Boolean} in Phase 4. */
public final class IfStmtNode extends StatementNode {

    private final ExpressionNode condition;
    private final BlockNode thenBlock;
    private final ElseBranchNode elseBranch;

    public IfStmtNode(ExpressionNode condition, BlockNode thenBlock, ElseBranchNode elseBranch, SourceSpan span) {
        super(AstKind.IF_STMT, span);
        this.condition = Objects.requireNonNull(condition);
        this.thenBlock = Objects.requireNonNull(thenBlock);
        this.elseBranch = elseBranch;
    }

    public ExpressionNode condition() {
        return condition;
    }

    public BlockNode thenBlock() {
        return thenBlock;
    }

    public Optional<ElseBranchNode> elseBranch() {
        return Optional.ofNullable(elseBranch);
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>();
        kids.add(condition);
        kids.add(thenBlock);
        if (elseBranch != null) {
            kids.add(elseBranch);
        }
        return List.copyOf(kids);
    }
}
