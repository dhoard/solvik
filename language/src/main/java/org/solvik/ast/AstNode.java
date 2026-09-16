/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast;

import java.util.List;
import java.util.Objects;
import org.solvik.source.SourceSpan;

/**
 * Base class of all immutable Solvik syntax-AST nodes.
 *
 * <p>A syntax node carries its authoritative {@link SourceSpan} and ordered children. It has no
 * dependency on executable Truffle nodes; lowering maps these nodes to runtime representations in
 * later phases.
 */
public abstract class AstNode {

    private final AstKind kind;
    private final SourceSpan span;

    protected AstNode(AstKind kind, SourceSpan span) {
        this.kind = Objects.requireNonNull(kind);
        this.span = Objects.requireNonNull(span, "every AST node must carry a source span");
    }

    public final AstKind kind() {
        return kind;
    }

    /** The authoritative half-open span {@code [startOffset, endOffset)} of this node. */
    public final SourceSpan span() {
        return span;
    }

    /** Direct child nodes in source order; leaf nodes return an empty list. */
    public abstract List<AstNode> children();

    /** An indented structural rendering (node kinds only, no source text) used by tree-shape tests. */
    public final String shapeTree() {
        StringBuilder sb = new StringBuilder();
        appendShape(sb, 0);
        return sb.toString();
    }

    private void appendShape(StringBuilder sb, int indent) {
        sb.append("  ".repeat(indent)).append(kind.name());
        List<AstNode> kids = children();
        if (!kids.isEmpty()) {
            sb.append(" {\n");
            for (AstNode child : kids) {
                child.appendShape(sb, indent + 1);
            }
            sb.append("  ".repeat(indent)).append("}\n");
        } else {
            sb.append("\n");
        }
    }
}
