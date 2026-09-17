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
