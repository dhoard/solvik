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
import org.solvik.source.SourceSpan;

/**
 * One variant of an {@code enum} declaration, e.g. {@code Ok(T)} in
 * {@code enum Result<T, E> { Ok(T) Error(E) }} (docs/LANGUAGE_SPEC.md section 12). An enum variant
 * is a nested nominal constructor: it names the variant and carries an ordered list of value types,
 * which may reference the enum's own type parameters. A variant with no values omits the
 * parentheses and carries an empty value list.
 *
 * <p>The variant's values are positional; the language has no field names on variants, and match
 * destructuring (a later phase) binds them by position.
 */
public final class EnumVariantNode extends AstNode {

    private final String name;
    private final List<TypeRefNode> valueTypes;

    public EnumVariantNode(String name, List<TypeRefNode> valueTypes, SourceSpan span) {
        super(AstKind.ENUM_VARIANT, span);
        this.name = Objects.requireNonNull(name);
        this.valueTypes = List.copyOf(valueTypes);
    }

    public String name() {
        return name;
    }

    /** The written value types, in declaration order; empty for a value-less variant. */
    public List<TypeRefNode> valueTypes() {
        return valueTypes;
    }

    @Override
    public List<AstNode> children() {
        return new ArrayList<>(valueTypes);
    }
}
