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
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.source.SourceSpan;

/**
 * A {@code regex <pattern>} {@code switch} case label (docs/LANGUAGE_SPEC.md sections 13 and 14),
 * for example {@code case regex r#"^\d+$"#:}. The pattern is a normal or raw string literal
 * expression; the static semantic pass compiles it through the portable regex dialect and records
 * the compiled pattern so lowering reuses it for every execution.
 */
public final class RegexCaseLabelNode extends CaseLabelNode {

    private final ExpressionNode pattern;

    public RegexCaseLabelNode(ExpressionNode pattern, SourceSpan span) {
        super(AstKind.REGEX_CASE_LABEL, span);
        this.pattern = Objects.requireNonNull(pattern);
    }

    /** The string literal pattern, a {@code StringLiteralNode} or a {@code RawStringLiteralNode}. */
    public ExpressionNode pattern() {
        return pattern;
    }

    @Override
    public List<AstNode> children() {
        return List.of(pattern);
    }
}
