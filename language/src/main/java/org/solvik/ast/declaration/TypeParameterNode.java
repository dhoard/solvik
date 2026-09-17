/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.declaration;

import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * One declared type parameter of a generic declaration, e.g. {@code T} in {@code class Box<T>}
 * (docs/LANGUAGE_SPEC.md section 11). The initial language has no bounds, so a type parameter is a
 * bare name.
 */
public final class TypeParameterNode extends AstNode {

    private final String name;

    public TypeParameterNode(String name, SourceSpan span) {
        super(AstKind.TYPE_PARAMETER, span);
        this.name = Objects.requireNonNull(name);
    }

    public String name() {
        return name;
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}
