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
import java.util.Optional;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.ast.statement.BindingKind;
import org.solvik.source.SourceSpan;

/**
 * A class property declaration {@code val name: Type [= initializer]} or
 * {@code var name: Type [= initializer]} (docs/LANGUAGE_SPEC.md section 7). Unlike a local
 * declaration the initializer is optional; a property without one must be assigned on every
 * successful constructor path.
 */
public final class PropertyDeclNode extends AstNode {

    private final BindingKind bindingKind;
    private final String name;
    private final TypeRefNode declaredType;
    private final ExpressionNode initializer;

    public PropertyDeclNode(BindingKind bindingKind, String name, TypeRefNode declaredType, ExpressionNode initializer, SourceSpan span) {
        super(AstKind.PROPERTY_DECL, span);
        this.bindingKind = Objects.requireNonNull(bindingKind);
        this.name = Objects.requireNonNull(name);
        this.declaredType = declaredType;
        this.initializer = initializer;
    }

    public BindingKind bindingKind() {
        return bindingKind;
    }

    public String name() {
        return name;
    }

    /** The written type reference when the property has an explicit annotation. */
    public Optional<TypeRefNode> declaredType() {
        return Optional.ofNullable(declaredType);
    }

    /** The declaration initializer when present. */
    public Optional<ExpressionNode> initializer() {
        return Optional.ofNullable(initializer);
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>();
        if (declaredType != null) {
            kids.add(declaredType);
        }
        if (initializer != null) {
            kids.add(initializer);
        }
        return List.copyOf(kids);
    }
}
