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
