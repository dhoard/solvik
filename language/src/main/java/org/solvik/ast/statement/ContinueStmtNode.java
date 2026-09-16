/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.statement;

import java.util.List;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/** A {@code continue;} statement. It is legal only inside a loop. */
public final class ContinueStmtNode extends StatementNode {

    public ContinueStmtNode(SourceSpan span) {
        super(AstKind.CONTINUE_STMT, span);
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}
