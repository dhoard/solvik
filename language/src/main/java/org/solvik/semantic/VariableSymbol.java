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
 * A variable binding: a {@code val}/{@code var} local or a function parameter. {@code val} locals
 * and parameters are immutable; {@code var} locals are mutable. Definite assignment is tracked here
 * so reads of a not-yet-initialized binding can be diagnosed.
 */
public final class VariableSymbol extends Symbol {

    private final Type type;
    private final boolean mutable;
    private final boolean parameter;
    private boolean initialized;

    VariableSymbol(String name, SourceSpan declarationSpan, Type type, boolean mutable, boolean parameter) {
        super(name, declarationSpan);
        this.type = Objects.requireNonNull(type);
        this.mutable = mutable;
        this.parameter = parameter;
    }

    public Type type() {
        return type;
    }

    public boolean isMutable() {
        return mutable;
    }

    public boolean isParameter() {
        return parameter;
    }

    public boolean isInitialized() {
        return initialized;
    }

    void markInitialized() {
        this.initialized = true;
    }
}
