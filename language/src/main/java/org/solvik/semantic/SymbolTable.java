/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.semantic;

import java.util.Objects;
import java.util.Optional;

/**
 * The lexical symbol table: a stack of {@link Scope}s. The outermost scope holds source-file
 * declarations (functions); each block, function body, and {@code for} statement pushes a nested
 * scope, so a nested declaration may legally shadow an outer one.
 */
public final class SymbolTable {

    private Scope current;

    public SymbolTable() {
        this.current = new Scope(null);
    }

    public void enterScope() {
        current = new Scope(current);
    }

    public void exitScope() {
        if (current.parent() == null) {
            throw new IllegalStateException("cannot exit the outermost scope");
        }
        current = current.parent();
    }

    /** Declares in the current scope; false means the name is already declared there. */
    public boolean declare(Symbol symbol) {
        return current.declare(Objects.requireNonNull(symbol));
    }

    /** Resolves a name from the innermost scope outward. */
    public Optional<Symbol> resolve(String name) {
        return current.lookup(name);
    }

    /** Resolves a name in the current scope only, without walking outward. */
    public Optional<Symbol> resolveLocal(String name) {
        return current.lookupLocal(name);
    }

    /** Current nesting depth; zero is the outermost scope. Exposed for tests and diagnostics. */
    public int depth() {
        int depth = 0;
        for (Scope scope = current.parent(); scope != null; scope = scope.parent()) {
            depth++;
        }
        return depth;
    }
}
