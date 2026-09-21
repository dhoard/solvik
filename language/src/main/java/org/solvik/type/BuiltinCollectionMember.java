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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A computed member of a built-in collection ({@code List}, {@code Set}, {@code Map}, or {@code Stack})
 * before its type parameters are substituted by a receiver's type arguments (docs/LANGUAGE_SPEC.md
 * section 11). A member is a read-only {@code val} property or a callable method.
 *
 * <p>Each parameter slot is written as either a {@link TypeParameterType} of the collection (for a
 * slot bound to a type parameter) or a concrete type (for a slot such as an {@code Integer} index).
 * {@link #substitutedParameterTypes(Map)} applies the receiver's identity substitution to every slot
 * so a concrete type is kept and only the bound slots are replaced; {@link #substitutedReturnType(Map)}
 * does the same for the return type, so {@code List.get(index)} yields the element type once a
 * concrete receiver supplies its binding.
 */
public final class BuiltinCollectionMember {

    private final String name;
    private final List<Type> parameterTypes;
    private final Type returnType;
    private final boolean property;

    public BuiltinCollectionMember(String name, List<Type> parameterTypes, Type returnType, boolean property) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(parameterTypes);
        Objects.requireNonNull(returnType);
        this.name = name;
        this.parameterTypes = List.copyOf(parameterTypes);
        this.returnType = returnType;
        this.property = property;
    }

    public String name() {
        return name;
    }

    /** The declared parameter slot types, each a {@link TypeParameterType} or a concrete type. */
    public List<Type> parameterTypes() {
        return parameterTypes;
    }

    /** The declared return type, written with the collection's type parameters. */
    public Type returnType() {
        return returnType;
    }

    /** Whether this member is a read-only {@code val} property rather than a callable method. */
    public boolean isProperty() {
        return property;
    }

    /** The parameter slot types after substituting the receiver's bindings for the collection's type parameters. */
    public List<Type> substitutedParameterTypes(Map<TypeParameterType, Type> substitution) {
        List<Type> types = new ArrayList<>(parameterTypes.size());
        for (Type slot : parameterTypes) {
            types.add(slot.substitute(substitution));
        }
        return types;
    }

    /** The return type after substituting the receiver's bindings for the collection's type parameters. */
    public Type substitutedReturnType(Map<TypeParameterType, Type> substitution) {
        return returnType.substitute(substitution);
    }
}
