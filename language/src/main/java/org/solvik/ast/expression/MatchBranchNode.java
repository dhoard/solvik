/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstNode;
import org.solvik.ast.pattern.PatternNode;
import org.solvik.source.SourceSpan;

/**
 * One {@code pattern => result} branch of a {@code match} expression
 * (docs/LANGUAGE_SPEC.md section 12). The pattern is matched against the scrutinee and the result
 * expression is evaluated when it matches; the branch itself is not a value-producing expression,
 * so it extends {@link AstNode} rather than {@link ExpressionNode}.
 */
public final class MatchBranchNode extends AstNode {

    private final PatternNode pattern;
    private final ExpressionNode result;

    public MatchBranchNode(PatternNode pattern, ExpressionNode result, SourceSpan span) {
        super(org.solvik.ast.AstKind.MATCH_BRANCH, span);
        this.pattern = Objects.requireNonNull(pattern);
        this.result = Objects.requireNonNull(result);
    }

    public PatternNode pattern() {
        return pattern;
    }

    public ExpressionNode result() {
        return result;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>();
        kids.add(pattern);
        kids.add(result);
        return List.copyOf(kids);
    }
}
