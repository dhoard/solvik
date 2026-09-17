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
import java.util.Optional;

/**
 * The compile-time type of a declared type parameter, e.g. {@code T} in {@code class Box<T>}
 * (docs/LANGUAGE_SPEC.md section 11). Identity is nominal: one instance per declared parameter, so
 * two declarations' parameters are never the same type even when they share a name.
 *
 * <p>The initial language has no type-parameter bounds, so the only declared supertype of a type
 * parameter is {@code Any}. At a use site a type parameter is replaced before assignability is
 * decided; when the parameter is not replaced (a malformed call), treating it as {@code Any} keeps
 * checking from inventing a narrower relationship than the language guarantees.
 */
public final class TypeParameterType extends Type {

    public TypeParameterType(String name) {
        super(Objects.requireNonNull(name));
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(AnyType.INSTANCE);
    }

    @Override
    public Type substitute(Map<TypeParameterType, Type> mapping) {
        Type replacement = mapping.get(this);
        return replacement != null ? replacement : this;
    }
}
