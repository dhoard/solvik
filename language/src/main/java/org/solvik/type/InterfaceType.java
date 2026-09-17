/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
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

    public InterfaceType(String name) {
        super(name);
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
    public void resolveSuperInterfaceTypes(List<InterfaceType> resolved) {
        superInterfaces.clear();
        superInterfaces.addAll(Objects.requireNonNull(resolved));
    }
}
