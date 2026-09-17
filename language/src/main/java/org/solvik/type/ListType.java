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

import java.util.List;
import java.util.Optional;

/**
 * The built-in immutable collection type {@code List<T>} (docs/LANGUAGE_SPEC.md section 11). It is a
 * nominal generic type with one type parameter, {@code T}, that derives from {@code Object}. Its
 * members are baked into static analysis rather than declared in source: {@code val size: Int} and
 * {@code func get(index: Int): T}, where an out-of-range index raises a Solvik runtime bounds error.
 *
 * <p>Collection literals are deferred, so there is no supported way to construct a {@code List}
 * value yet. The type still participates in generic checking: {@code List<String>} is a subtype of
 * {@code Object}, is invariant in {@code T}, and its {@code get} result type is the element type.
 */
public final class ListType extends Type {

    public static final ListType INSTANCE = new ListType();

    private final TypeParameterType elementParameter = new TypeParameterType("T");

    private ListType() {
        super("List");
    }

    @Override
    public List<TypeParameterType> typeParameters() {
        return List.of(elementParameter);
    }

    /** The declared element type parameter, for substitution by the analyzer. */
    public TypeParameterType elementParameter() {
        return elementParameter;
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(ObjectType.INSTANCE);
    }
}
