/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A compile-time Solvik type (docs/ARCHITECTURE.md "Type System").
 *
 * <p>This is an explicit compiler type model, not a wrapper around JVM classes. Type identity is
 * nominal: the built-in types are singletons, and user-declared nominal types become identity
 * singletons when class and interface declarations are collected.
 *
 * <p>The subtype relation is reflexive, transitive along {@link #superType()} and the declared
 * {@link #interfaceTypes()} edges, and makes the bottom type {@code Nothing} a subtype of every
 * type. The top type is {@code Any}. Interfaces join the relation from Phase 8: a class is a
 * subtype of every interface it implements transitively, and an interface is a subtype of every
 * interface it extends. Nullability, enums, generics, and type parameters are deliberately absent
 * until their phases.
 */
public abstract class Type {

    private final String name;

    protected Type(String name) {
        this.name = Objects.requireNonNull(name);
    }

    /** The user-visible name; for built-ins this is the spelling usable as a type reference. */
    public final String name() {
        return name;
    }

    /** The unique direct supertype, or empty for the top and bottom types. */
    public Optional<Type> superType() {
        return Optional.empty();
    }

    /**
     * The interfaces declared directly on this type: a class's {@code implements} list or an
     * interface's {@code extends} list. Built-in and non-nominal types declare none.
     */
    public List<Type> interfaceTypes() {
        return List.of();
    }

    /** Whether this is the bottom type {@code Nothing}, a subtype of every type. */
    public boolean isBottom() {
        return false;
    }

    /** Whether a value of this type may be used where {@code other} is required. */
    public final boolean isAssignableTo(Type other) {
        return isSubtypeOf(other);
    }

    /**
     * Whether this type is a subtype of {@code other} under the nominal hierarchy, walked over both
     * the single-inheritance superclass chain and the declared interface edges. The walk is
     * cycle-safe so a malformed declaration graph cannot loop forever.
     */
    public final boolean isSubtypeOf(Type other) {
        Objects.requireNonNull(other, "other");
        if (this == other || isBottom()) {
            return true;
        }
        Map<Type, Boolean> visited = new IdentityHashMap<>();
        visited.put(this, Boolean.TRUE);
        return reaches(other, visited);
    }

    private boolean reaches(Type other, Map<Type, Boolean> visited) {
        for (Type parent : immediateSupertypes()) {
            if (parent == other) {
                return true;
            }
            if (visited.put(parent, Boolean.TRUE) == null && parent.reaches(other, visited)) {
                return true;
            }
        }
        return false;
    }

    private List<Type> immediateSupertypes() {
        List<Type> parents = new ArrayList<>(interfaceTypes());
        superType().ifPresent(parents::add);
        return parents;
    }

    @Override
    public final String toString() {
        return name;
    }
}
