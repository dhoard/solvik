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
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * One case of a {@code switch} statement (docs/LANGUAGE_SPEC.md section 13): either a list of labels
 * or the single {@code default}. The body is an implicit block, so it is a {@link BlockNode} exactly
 * like a braced block. A {@code default} case has no labels.
 */
public final class SwitchCaseNode extends AstNode {

    private final boolean defaultCase;
    private final List<CaseLabelNode> labels;
    private final BlockNode body;

    public SwitchCaseNode(boolean defaultCase, List<CaseLabelNode> labels, BlockNode body, SourceSpan span) {
        super(AstKind.SWITCH_CASE, span);
        this.defaultCase = defaultCase;
        this.labels = List.copyOf(Objects.requireNonNull(labels));
        this.body = Objects.requireNonNull(body);
        if (defaultCase && !this.labels.isEmpty()) {
            throw new IllegalArgumentException("the default case has no labels");
        }
    }

    /** Whether this is the {@code default} case rather than a labelled case. */
    public boolean isDefault() {
        return defaultCase;
    }

    /** The labels of a non-default case, in source order; empty for {@code default}. */
    public List<CaseLabelNode> labels() {
        return labels;
    }

    /** The implicit block forming this case's body. */
    public BlockNode body() {
        return body;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>(labels);
        kids.add(body);
        return List.copyOf(kids);
    }
}
