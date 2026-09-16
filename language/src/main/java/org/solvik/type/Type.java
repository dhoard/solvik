/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Objects;
import java.util.Optional;

/**
 * A compile-time Solvik type (docs/ARCHITECTURE.md "Type System").
 *
 * <p>This is an explicit compiler type model, not a wrapper around JVM classes. Type identity is
 * nominal: the built-in types are singletons, and user-declared nominal types become identity
 * singletons when class declarations land in later phases.
 *
 * <p>The subtype relation is reflexive, transitive along {@link #superType()}, and makes the bottom
 * type {@code Nothing} a subtype of every type. The top type is {@code Any}. Nullability,
 * interfaces, enums, generics, and type parameters are deliberately absent until their phases.
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

    /** Whether this is the bottom type {@code Nothing}, a subtype of every type. */
    public boolean isBottom() {
        return false;
    }

    /** Whether a value of this type may be used where {@code other} is required. */
    public final boolean isAssignableTo(Type other) {
        return isSubtypeOf(other);
    }

    /** Whether this type is a subtype of {@code other} under the nominal built-in hierarchy. */
    public final boolean isSubtypeOf(Type other) {
        Objects.requireNonNull(other, "other");
        if (this == other) {
            return true;
        }
        if (isBottom()) {
            return true;
        }
        for (Type current = superType().orElse(null); current != null; current = current.superType().orElse(null)) {
            if (current == other) {
                return true;
            }
        }
        return false;
    }

    @Override
    public final String toString() {
        return name;
    }
}
