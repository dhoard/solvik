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
 * A declared class property (docs/LANGUAGE_SPEC.md section 7): the compile-time descriptor of one
 * statically declared object field. {@code val} properties are immutable after construction;
 * {@code var} properties are mutable. {@code index} is the field's position in the class layout and
 * is stable for the lifetime of the program.
 *
 * <p>A {@code delegate val} declaration produces a property symbol with {@link #isDelegate()} set
 * (docs/LANGUAGE_SPEC.md section 9): it is ordinary immutable property storage that additionally
 * supplies the interface members of its declared type to the declaring class.
 */
public final class PropertySymbol extends Symbol {

    private final Type type;
    private final boolean mutable;
    private final boolean hasInitializer;
    private final boolean delegate;
    private final int index;

    PropertySymbol(String name, SourceSpan declarationSpan, Type type, boolean mutable, boolean hasInitializer, int index) {
        this(name, declarationSpan, type, mutable, hasInitializer, false, index);
    }

    PropertySymbol(String name, SourceSpan declarationSpan, Type type, boolean mutable, boolean hasInitializer, boolean delegate, int index) {
        super(name, declarationSpan);
        this.type = Objects.requireNonNull(type);
        this.mutable = mutable;
        this.hasInitializer = hasInitializer;
        this.delegate = delegate;
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

    /** Whether this property was declared with {@code delegate} and forwards interface members. */
    public boolean isDelegate() {
        return delegate;
    }

    /** The field's position in the statically declared class layout. */
    public int index() {
        return index;
    }
}
