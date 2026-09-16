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
 * A written type reference by simple name. Phase 1 records the name only; resolution to the
 * compiler type model happens in later phases.
 */
public final class TypeRefNode extends AstNode {

    private final String name;

    public TypeRefNode(String name, SourceSpan span) {
        super(AstKind.TYPE_REF, span);
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
