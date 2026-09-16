/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.expression;

import java.util.List;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/** Base class for literal expressions carrying their source spelling. */
public abstract class LiteralNode extends ExpressionNode {

    private final String lexeme;

    protected LiteralNode(AstKind kind, String lexeme, SourceSpan span) {
        super(kind, span);
        this.lexeme = lexeme;
    }

    /** Exact source text of the literal, including quotes for strings. */
    public final String lexeme() {
        return lexeme;
    }

    @Override
    public final List<AstNode> children() {
        return List.of();
    }
}
