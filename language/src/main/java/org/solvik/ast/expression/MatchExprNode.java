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
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * An expression-oriented {@code match <scrutinee> { branches }} (docs/LANGUAGE_SPEC.md section 12).
 * The scrutinee is a value-producing expression and each branch pairs a pattern with a result
 * expression. The compiler checks that the branches are exhaustive for a known closed variant set
 * and that every branch result is assignable to the match's nearest common declared supertype.
 */
public final class MatchExprNode extends ExpressionNode {

    private final ExpressionNode scrutinee;
    private final List<MatchBranchNode> branches;

    public MatchExprNode(ExpressionNode scrutinee, List<MatchBranchNode> branches, SourceSpan span) {
        super(AstKind.MATCH_EXPR, span);
        this.scrutinee = Objects.requireNonNull(scrutinee);
        this.branches = List.copyOf(Objects.requireNonNull(branches));
    }

    public ExpressionNode scrutinee() {
        return scrutinee;
    }

    public List<MatchBranchNode> branches() {
        return branches;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>();
        kids.add(scrutinee);
        kids.addAll(branches);
        return List.copyOf(kids);
    }
}
