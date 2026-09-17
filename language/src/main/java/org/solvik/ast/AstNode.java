/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
