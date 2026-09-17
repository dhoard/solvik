/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Objects;

/**
 * A nullable type {@code T?} (docs/LANGUAGE_SPEC.md section 5): the value set of the non-null type
 * {@code T} together with {@code null}.
 *
 * <p>Instances are canonical per {@link Type} instance through {@link Type#nullableView()}, so
 * identity comparison of nullable types is as reliable as it is for the built-in and nominal
 * non-null types. A nullable type is never nested, and {@code Null} is already the type of
 * {@code null}, so the factory returns those operands unchanged.
 *
 * <p>Subtyping is handled by {@link Type#isSubtypeOf(Type)}: {@code S} is assignable to {@code T?}
 * whenever {@code S} is assignable to {@code T}, {@code S?} is assignable to {@code T?} whenever
 * {@code S} is assignable to {@code T}, and {@code S?} is never assignable to a non-null type.
 */
public final class NullableType extends Type {

    private final Type inner;

    NullableType(Type inner) {
        super(Objects.requireNonNull(inner).name() + "?");
        if (inner instanceof NullableType || inner instanceof NullType) {
            throw new IllegalArgumentException("a nullable type is never nested: " + inner);
        }
        this.inner = inner;
    }

    /** The non-null type whose value set this type extends with {@code null}. */
    public Type inner() {
        return inner;
    }

    @Override
    public boolean isNullable() {
        return true;
    }

    @Override
    public Type nonNullType() {
        return inner;
    }
}
