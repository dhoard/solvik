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

/** A single typed function parameter, e.g. {@code a: Int}. */
public final class ParameterNode extends AstNode {

    private final String name;
    private final TypeRefNode type;

    public ParameterNode(String name, TypeRefNode type, SourceSpan span) {
        super(AstKind.PARAMETER, span);
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
    }

    public String name() {
        return name;
    }

    public TypeRefNode type() {
        return type;
    }

    @Override
    public List<AstNode> children() {
        return List.of(type);
    }
}
