/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
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
