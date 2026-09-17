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
 * A written type reference: a simple name and, from Phase 10, an optional {@code ?} marking the
 * nullable type {@code T?} (docs/LANGUAGE_SPEC.md section 5). Phase 1 records the name only;
 * resolution to the compiler type model happens in later phases.
 */
public final class TypeRefNode extends AstNode {

    private final String name;
    private final boolean nullable;

    public TypeRefNode(String name, SourceSpan span) {
        this(name, false, span);
    }

    public TypeRefNode(String name, boolean nullable, SourceSpan span) {
        super(AstKind.TYPE_REF, span);
        this.name = Objects.requireNonNull(name);
        this.nullable = nullable;
    }

    public String name() {
        return name;
    }

    /** Whether the reference was written with a trailing {@code ?}. */
    public boolean isNullable() {
        return nullable;
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}
