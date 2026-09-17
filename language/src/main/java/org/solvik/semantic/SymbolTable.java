/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

    private final Scope root;
    private Scope current;

    public SymbolTable() {
        this.root = new Scope(null);
        this.current = root;
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

    /**
     * Resolves a name in the lexical scopes only, excluding the outermost declaration scope. Used by
     * module-aware resolution so a local never hides a module declaration only by accident.
     */
    public Optional<Symbol> resolveLocalChain(String name) {
        for (Scope scope = current; scope != null && scope != root; scope = scope.parent()) {
            Symbol found = scope.lookupLocal(name).orElse(null);
            if (found != null) {
                return Optional.of(found);
            }
        }
        return Optional.empty();
    }

    /** Resolves a name in the outermost declaration scope only (the default module and built-ins). */
    public Optional<Symbol> resolveInRoot(String name) {
        return root.lookupLocal(name);
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
