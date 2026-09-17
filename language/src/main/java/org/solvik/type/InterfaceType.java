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
import java.util.Objects;
import java.util.Optional;

/**
 * The compile-time type of a user-declared interface (docs/LANGUAGE_SPEC.md section 8). Identity is
 * nominal: one instance per interface declaration.
 *
 * <p>An interface is a value type in the {@code Object} root hierarchy, so its implicit direct
 * supertype is {@code Object}; {@link #superType()} reports it. Its declared {@code extends} list is
 * installed during semantic collection via {@link #resolveSuperInterfaceTypes(List)}, which is why
 * the subtype walk in {@link Type#isSubtypeOf(Type)} observes the completed interface graph before
 * any body is checked.
 *
 * <p>An interface type is never constructible from source, so it is used only as a static contract
 * type: a parameter, local, or return type that a class instance may be assigned to.
 */
public final class InterfaceType extends Type {

    private final List<Type> superInterfaces = new ArrayList<>();
    private List<TypeParameterType> typeParameters = List.of();

    public InterfaceType(String name) {
        super(name);
    }

    @Override
    public List<TypeParameterType> typeParameters() {
        return typeParameters;
    }

    /**
     * Installs this interface's declared type parameters. Called once during declaration collection,
     * before any member type is resolved, so a member may reference its interface's own parameters.
     */
    public void resolveTypeParameters(List<TypeParameterType> resolved) {
        this.typeParameters = List.copyOf(Objects.requireNonNull(resolved));
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(ObjectType.INSTANCE);
    }

    @Override
    public List<Type> interfaceTypes() {
        return List.copyOf(superInterfaces);
    }

    /**
     * Installs the resolved direct {@code extends} interfaces. Called by semantic analysis after all
     * interface names are collected, before any conforming class is checked.
     */
    public void resolveSuperInterfaceTypes(List<? extends Type> resolved) {
        superInterfaces.clear();
        superInterfaces.addAll(Objects.requireNonNull(resolved));
    }
}
