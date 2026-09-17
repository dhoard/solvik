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
