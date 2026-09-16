/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Optional;

/**
 * The abstract numeric root {@code Number} (docs/LANGUAGE_SPEC.md section 4). It has no values of
 * its own; {@code Byte}, {@code Short}, {@code Int}, {@code Long}, {@code Float}, and {@code Double}
 * are its subtypes. Using {@code Number} as a declared type accepts any numeric value, but
 * arithmetic still requires same-type operands.
 */
public final class NumberType extends Type {

    public static final NumberType INSTANCE = new NumberType();

    private NumberType() {
        super("Number");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(ObjectType.INSTANCE);
    }
}
