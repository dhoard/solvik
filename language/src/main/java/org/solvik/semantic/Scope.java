/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.semantic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** A single lexical scope. Scopes nest; resolution walks outward to the enclosing scopes. */
final class Scope {

    private final Scope parent;
    private final Map<String, Symbol> symbols = new LinkedHashMap<>();

    Scope(Scope parent) {
        this.parent = parent;
    }

    Scope parent() {
        return parent;
    }

    /** Adds a symbol when the name is free in this scope; false means a duplicate declaration. */
    boolean declare(Symbol symbol) {
        if (symbols.containsKey(symbol.name())) {
            return false;
        }
        symbols.put(symbol.name(), symbol);
        return true;
    }

    Optional<Symbol> lookupLocal(String name) {
        return Optional.ofNullable(symbols.get(name));
    }

    /** Resolves a name from this scope outward, innermost first. */
    Optional<Symbol> lookup(String name) {
        for (Scope scope = this; scope != null; scope = scope.parent) {
            Symbol found = scope.symbols.get(name);
            if (found != null) {
                return Optional.of(found);
            }
        }
        return Optional.empty();
    }
}
