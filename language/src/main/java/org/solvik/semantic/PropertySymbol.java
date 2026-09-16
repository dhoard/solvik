/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.semantic;

import java.util.Objects;
import org.solvik.source.SourceSpan;
import org.solvik.type.Type;

/**
 * A declared class property (docs/LANGUAGE_SPEC.md section 7): the compile-time descriptor of one
 * statically declared object field. {@code val} properties are immutable after construction;
 * {@code var} properties are mutable. {@code index} is the field's position in the class layout and
 * is stable for the lifetime of the program.
 */
public final class PropertySymbol extends Symbol {

    private final Type type;
    private final boolean mutable;
    private final boolean hasInitializer;
    private final int index;

    PropertySymbol(String name, SourceSpan declarationSpan, Type type, boolean mutable, boolean hasInitializer, int index) {
        super(name, declarationSpan);
        this.type = Objects.requireNonNull(type);
        this.mutable = mutable;
        this.hasInitializer = hasInitializer;
        this.index = index;
    }

    public Type type() {
        return type;
    }

    public boolean isMutable() {
        return mutable;
    }

    /** Whether the property declaration itself supplies an initializer expression. */
    public boolean hasInitializer() {
        return hasInitializer;
    }

    /** The field's position in the statically declared class layout. */
    public int index() {
        return index;
    }
}
