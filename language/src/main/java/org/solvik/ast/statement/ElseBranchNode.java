/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.statement;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * The {@code else} part of an if statement: either a nested {@link IfStmtNode} (chained
 * {@code else if}) or a replacement {@link BlockNode}.
 */
public final class ElseBranchNode extends AstNode {

    private final IfStmtNode chainedIf;
    private final BlockNode block;

    private ElseBranchNode(IfStmtNode chainedIf, BlockNode block, SourceSpan span) {
        super(AstKind.ELSE_BRANCH, span);
        this.chainedIf = chainedIf;
        this.block = block;
    }

    public static ElseBranchNode ofBlock(BlockNode block, SourceSpan span) {
        return new ElseBranchNode(null, Objects.requireNonNull(block), span);
    }

    public static ElseBranchNode ofChainedIf(IfStmtNode chainedIf, SourceSpan span) {
        return new ElseBranchNode(Objects.requireNonNull(chainedIf), null, span);
    }

    public boolean isChainedIf() {
        return chainedIf != null;
    }

    public Optional<IfStmtNode> chainedIf() {
        return Optional.ofNullable(chainedIf);
    }

    public Optional<BlockNode> block() {
        return Optional.ofNullable(block);
    }

    @Override
    public List<AstNode> children() {
        return chainedIf != null ? List.of(chainedIf) : List.of(block);
    }
}
