/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.declaration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.statement.BlockNode;
import org.solvik.source.SourceSpan;

/**
 * A class {@code init(parameters) { ... }} declaration (docs/LANGUAGE_SPEC.md section 7). A class
 * has at most one {@code init}; calling the class name invokes it.
 */
public final class InitDeclNode extends AstNode {

    private final List<ParameterNode> parameters;
    private final BlockNode body;

    public InitDeclNode(List<ParameterNode> parameters, BlockNode body, SourceSpan span) {
        super(AstKind.INIT_DECL, span);
        this.parameters = List.copyOf(parameters);
        this.body = Objects.requireNonNull(body);
    }

    public List<ParameterNode> parameters() {
        return parameters;
    }

    public BlockNode body() {
        return body;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>(parameters);
        kids.add(body);
        return List.copyOf(kids);
    }
}
