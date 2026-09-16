/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Optional;

/** The built-in IEEE 754 single-precision type {@code Float} (docs/LANGUAGE_SPEC.md section 4). */
public final class FloatType extends Type {

    public static final FloatType INSTANCE = new FloatType();

    private FloatType() {
        super("Float");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(NumberType.INSTANCE);
    }
}
