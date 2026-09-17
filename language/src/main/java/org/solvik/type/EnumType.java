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
import java.util.Objects;
import java.util.Optional;

/**
 * The compile-time type of a user-declared enum (docs/LANGUAGE_SPEC.md section 12, and the
 * {@code EnumType} entry of docs/ARCHITECTURE.md's type model). Identity is nominal: one instance
 * per enum declaration.
 *
 * <p>An enum is a closed nominal type whose values all derive from {@code Object}. Unlike a class or
 * interface it declares no supertype edges: a class may not extend an enum and an enum may not
 * extend or implement anything, because the complete variant set is known when the file is compiled.
 * Its variants are nested nominal constructors recorded by the semantic {@code EnumSymbol}, not
 * separate static types.
 *
 * <p>An enum may be generic ({@code enum Result<T, E>}); a written application is an invariant
 * {@link ParameterizedType} exactly like a generic class or interface.
 */
public final class EnumType extends Type {

    private List<TypeParameterType> typeParameters = List.of();

    public EnumType(String name) {
        super(Objects.requireNonNull(name));
    }

    @Override
    public List<TypeParameterType> typeParameters() {
        return typeParameters;
    }

    /**
     * Installs this enum's declared type parameters. Called once during declaration collection,
     * before any variant value type is resolved, so a variant may reference its enum's parameters.
     */
    public void resolveTypeParameters(List<TypeParameterType> resolved) {
        this.typeParameters = List.copyOf(Objects.requireNonNull(resolved));
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(ObjectType.INSTANCE);
    }
}
