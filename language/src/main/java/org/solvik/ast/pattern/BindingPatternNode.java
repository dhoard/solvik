/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.declaration.TypeRefNode;
import org.solvik.source.SourceSpan;

/**
 * A binding pattern (docs/LANGUAGE_SPEC.md section 12). The specification's sealed-subtype form is
 * {@code name: Type}, which matches a value of that subtype and binds {@code name} to it. A variant
 * argument may also be a bare name, which binds the value with the variant's known value type, so
 * the written type is optional.
 */
public final class BindingPatternNode extends PatternNode {

    private final String name;
    private final TypeRefNode typeRef;

    public BindingPatternNode(String name, TypeRefNode typeRef, SourceSpan span) {
        super(AstKind.BINDING_PATTERN, span);
        this.name = Objects.requireNonNull(name);
        this.typeRef = typeRef;
    }

    public String name() {
        return name;
    }

    /** The written subtype of a {@code name: Type} pattern, or empty for a bare variant binding. */
    public Optional<TypeRefNode> typeRef() {
        return Optional.ofNullable(typeRef);
    }

    @Override
    public List<AstNode> children() {
        if (typeRef == null) {
            return List.of();
        }
        ArrayList<AstNode> kids = new ArrayList<>();
        kids.add(typeRef);
        return List.copyOf(kids);
    }
}
