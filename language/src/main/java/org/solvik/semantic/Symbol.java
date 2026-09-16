/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.semantic;

import java.util.Objects;
import org.solvik.source.SourceSpan;

/**
 * A compile-time symbol: the name-resolution result for a declared function, parameter, or local.
 * Symbols are analysis-time objects distinct from syntax-AST nodes and from runtime values.
 */
public abstract class Symbol {

    private final String name;
    private final SourceSpan declarationSpan;

    protected Symbol(String name, SourceSpan declarationSpan) {
        this.name = Objects.requireNonNull(name);
        this.declarationSpan = Objects.requireNonNull(declarationSpan);
    }

    public final String name() {
        return name;
    }

    /** The source span of the declaration that introduced this symbol. */
    public final SourceSpan declarationSpan() {
        return declarationSpan;
    }
}
