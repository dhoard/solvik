/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.statement;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.declaration.TypeRefNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.source.SourceSpan;

/**
 * A {@code val} or {@code var} local declaration with an initializer. The type annotation is
 * syntactically optional; inference rules are a Phase 4 concern.
 */
public final class LocalDeclNode extends StatementNode {

    private final BindingKind bindingKind;
    private final String name;
    private final TypeRefNode declaredType;
    private final ExpressionNode initializer;

    public LocalDeclNode(BindingKind bindingKind, String name, TypeRefNode declaredType, ExpressionNode initializer, SourceSpan span) {
        super(AstKind.LOCAL_DECL, span);
        this.bindingKind = Objects.requireNonNull(bindingKind);
        this.name = Objects.requireNonNull(name);
        this.declaredType = declaredType;
        this.initializer = Objects.requireNonNull(initializer);
    }

    public BindingKind bindingKind() {
        return bindingKind;
    }

    public String name() {
        return name;
    }

    /** The written type annotation, or empty when only the initializer spelling exists. */
    public Optional<TypeRefNode> declaredType() {
        return Optional.ofNullable(declaredType);
    }

    public ExpressionNode initializer() {
        return initializer;
    }

    @Override
    public List<AstNode> children() {
        if (declaredType == null) {
            return List.of(initializer);
        }
        return List.of(declaredType, initializer);
    }
}
