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
package org.solvik.type;

import java.util.Map;
import java.util.Objects;

/**
 * A nullable type {@code T?} (docs/LANGUAGE_SPEC.md section 5): the value set of the non-null type
 * {@code T} together with {@code null}.
 *
 * <p>Instances are canonical per {@link Type} instance through {@link Type#nullableView()}, so
 * identity comparison of nullable types is as reliable as it is for the built-in and nominal
 * non-null types. A nullable type is never nested, and {@code Null} is already the type of
 * {@code null}, so the factory returns those operands unchanged.
 *
 * <p>Subtyping is handled by {@link Type#isSubtypeOf(Type)}: {@code S} is assignable to {@code T?}
 * whenever {@code S} is assignable to {@code T}, {@code S?} is assignable to {@code T?} whenever
 * {@code S} is assignable to {@code T}, and {@code S?} is never assignable to a non-null type.
 */
public final class NullableType extends Type {

    private final Type inner;

    NullableType(Type inner) {
        super(Objects.requireNonNull(inner).name() + "?");
        if (inner instanceof NullableType || inner instanceof NullType) {
            throw new IllegalArgumentException("a nullable type is never nested: " + inner);
        }
        this.inner = inner;
    }

    /** The non-null type whose value set this type extends with {@code null}. */
    public Type inner() {
        return inner;
    }

    @Override
    public boolean isNullable() {
        return true;
    }

    @Override
    public Type nonNullType() {
        return inner;
    }

    @Override
    public Type substitute(Map<TypeParameterType, Type> mapping) {
        Type substituted = inner.substitute(mapping);
        return substituted == inner ? this : substituted.nullableView();
    }
}
