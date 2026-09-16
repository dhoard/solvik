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
 * The compile-time type of a user-declared class (docs/LANGUAGE_SPEC.md sections 4 and 7). Identity
 * is nominal: one instance per class declaration, so two structurally identical classes are never
 * assignment-compatible (docs/ARCHITECTURE.md "Type System").
 *
 * <p>Every class ultimately derives from {@code Object}. When a class explicitly {@code extends}
 * another class, the resolved superclass type is installed during semantic collection via
 * {@link #resolveSuperType(Type)}; the installation happens before any body is checked, so the
 * subtype walk in {@link Type#isSubtypeOf(Type)} observes the completed hierarchy.
 */
public final class ClassType extends Type {

    private Type superType = ObjectType.INSTANCE;

    public ClassType(String name) {
        super(name);
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(superType);
    }

    /**
     * Installs the resolved direct supertype of this class. Called exactly once by semantic
     * analysis after all class names are collected; passing {@code null} restores the implicit
     * {@code Object} root (used when an inheritance cycle is broken for diagnosis).
     */
    public void resolveSuperType(Type resolved) {
        this.superType = Objects.requireNonNullElse(resolved, ObjectType.INSTANCE);
    }
}
