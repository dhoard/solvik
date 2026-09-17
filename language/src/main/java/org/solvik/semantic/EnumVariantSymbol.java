/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.semantic;

import java.util.List;
import java.util.Objects;
import org.solvik.ast.declaration.EnumVariantNode;
import org.solvik.source.SourceSpan;
import org.solvik.type.Type;

/**
 * A declared enum variant (docs/LANGUAGE_SPEC.md section 12): a nested nominal constructor whose
 * ordered value types may reference its enum's type parameters. The variant is not a static type of
 * its own; constructing it produces a value of the owning enum, so the variant records the
 * constructor shape only.
 *
 * <p>{@link #valueTypes()} are stored in the owning enum's type-parameter space. A use site
 * substitutes them with the inferred enum type arguments before checking the construction's
 * arguments, exactly as a class constructor's parameter types are substituted.
 */
public final class EnumVariantSymbol extends Symbol {

    private final EnumSymbol owner;
    private final List<Type> valueTypes;
    private final EnumVariantNode declaration;

    EnumVariantSymbol(EnumSymbol owner, String name, SourceSpan declarationSpan, List<Type> valueTypes, EnumVariantNode declaration) {
        super(name, declarationSpan);
        this.owner = Objects.requireNonNull(owner);
        this.valueTypes = List.copyOf(valueTypes);
        this.declaration = Objects.requireNonNull(declaration);
    }

    /** The enum this variant constructs a value of. */
    public EnumSymbol owner() {
        return owner;
    }

    /** The variant's ordered value types in the enum's type-parameter space. */
    public List<Type> valueTypes() {
        return valueTypes;
    }

    /** The syntax declaration, retained for diagnostics and source locations. */
    public EnumVariantNode declaration() {
        return declaration;
    }
}
