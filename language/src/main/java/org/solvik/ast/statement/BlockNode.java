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
import org.solvik.source.SourceSpan;

/** A brace-delimited block statement. Each block introduces a new lexical scope in Phase 4. */
public final class BlockNode extends StatementNode {

    private final List<StatementNode> statements;

    public BlockNode(List<StatementNode> statements, SourceSpan span) {
        super(AstKind.BLOCK, span);
        this.statements = List.copyOf(statements);
    }

    public List<StatementNode> statements() {
        return statements;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<AstNode> children() {
        return List.copyOf((List) statements);
    }
}
