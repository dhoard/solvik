/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

/**
 * The type of the {@code null} literal (docs/LANGUAGE_SPEC.md section 5). It is the only type with a
 * value that is not a value of any non-null type: {@code null} is assignable only to a nullable type
 * {@code T?}, so this type is not a subtype of any non-null type and is deliberately absent from the
 * built-in type namespace.
 *
 * <p>Because the bottom type {@code Nothing} is a subtype of every type, {@code NullType} is not the
 * bottom type; it is a distinct nullable type with exactly one value.
 */
public final class NullType extends Type {

    public static final NullType INSTANCE = new NullType();

    private NullType() {
        super("Null");
    }

    @Override
    public boolean isNullable() {
        return true;
    }
}
