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

    /**
     * Enters a scope that is a function boundary. Names inside it resolve only to that scope and its
     * descendants, never to anything declared in an enclosing function, which is what makes a
     * callable body see its own parameters and locals plus globals and nothing
     * between (docs/LANGUAGE_SPEC.md section 6, "Anonymous functions"). Globals live in the root and
     * module scopes reached by separate resolution, so a boundary does not hide them.
     */
    public void enterFunctionBoundaryScope() {
        current = new Scope(current, true);
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
            // A boundary is looked up (it holds the anonymous function's own parameters) but stops the
            // walk there: an enclosing function's bindings are not visible across it.
            if (scope.isFunctionBoundary()) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Whether {@code name} is declared in some enclosing function's scope, hidden by the innermost
     * function boundary in the current chain. Such a name is not visible and is not unknown either:
     * it is the dependency that an explicit capture list would have to declare, and it is reported as
     * the unlisted capture the specification defines for it (docs/LANGUAGE_SPEC.md section 6,
     * "Explicit immutable closure capture"). Returns false when no function boundary encloses the
     * current scope, so ordinary code is unaffected.
     *
     * <p>A top-level binding counts, because the specification makes it one: "a top-level
     * `var`, whether or not it is `mutable`, is therefore a local of the implicit main, not a
     * global". A top-level
     * {@code func} does not, because it is declared in the root scope this walk stops at, which is what
     * makes the spec's "Recursion through named top-level functions needs no capture" hold.
     */
    public boolean hiddenAcrossFunctionBoundary(String name) {
        Scope boundary = innermostFunctionBoundary();
        if (boundary == null) {
            return false;
        }
        for (Scope scope = boundary.parent(); scope != null && scope != root; scope = scope.parent()) {
            if (scope.lookupLocal(name).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The innermost function boundary enclosing the current scope, or {@code null} when the current scope
     * chain contains none. The scope returned holds a closure's own parameters, locals, and capture
     * bindings, so it is a valid scope to declare into; searching strictly <em>above</em> it is what
     * excludes those from being captured by their own closure.
     */
    private Scope innermostFunctionBoundary() {
        for (Scope scope = current; scope != null && scope != root; scope = scope.parent()) {
            if (scope.isFunctionBoundary()) {
                return scope;
            }
        }
        return null;
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
