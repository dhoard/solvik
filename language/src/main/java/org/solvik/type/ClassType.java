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
 * The compile-time type of a user-declared class (docs/LANGUAGE_SPEC.md sections 4, 7, and 8). Identity
 * is nominal: one instance per class declaration, so two structurally identical classes are never
 * assignment-compatible (docs/ARCHITECTURE.md "Type System").
 *
 * <p>Every class ultimately derives from {@code Object}. When a class explicitly {@code extends}
 * another class, the resolved superclass type is installed during semantic collection via
 * {@link #resolveSuperType(Type)}; the interfaces it {@code implements} are installed in the same
 * pass via {@link #resolveInterfaceTypes(List)}. Both installations happen before any body is
 * checked, so the subtype walk in {@link Type#isSubtypeOf(Type)} observes the completed hierarchy.
 */
public final class ClassType extends Type {

    private Type superType = ObjectType.INSTANCE;
    private final List<Type> interfaces = new ArrayList<>();
    private List<TypeParameterType> typeParameters = List.of();

    public ClassType(String name) {
        super(name);
    }

    @Override
    public List<TypeParameterType> typeParameters() {
        return typeParameters;
    }

    /**
     * Installs this class's declared type parameters. Called once during declaration collection,
     * before any member type is resolved, so a member may reference its class's own parameters.
     */
    public void resolveTypeParameters(List<TypeParameterType> resolved) {
        this.typeParameters = List.copyOf(Objects.requireNonNull(resolved));
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(superType);
    }

    @Override
    public List<Type> interfaceTypes() {
        return List.copyOf(interfaces);
    }

    /**
     * Installs the resolved direct supertype of this class. Called exactly once by semantic
     * analysis after all class names are collected; passing {@code null} restores the implicit
     * {@code Object} root (used when an inheritance cycle is broken for diagnosis).
     */
    public void resolveSuperType(Type resolved) {
        this.superType = Objects.requireNonNullElse(resolved, ObjectType.INSTANCE);
    }

    /**
     * Installs the resolved {@code implements} interfaces of this class. Called by semantic analysis
     * after all interface names are collected, before interface conformance is checked.
     */
    public void resolveInterfaceTypes(List<? extends Type> resolved) {
        interfaces.clear();
        interfaces.addAll(Objects.requireNonNull(resolved));
    }
}
