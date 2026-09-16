/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Optional;

/** The built-in IEEE 754 double-precision type {@code Double} (docs/LANGUAGE_SPEC.md section 4). */
public final class DoubleType extends Type {

    public static final DoubleType INSTANCE = new DoubleType();

    private DoubleType() {
        super("Double");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(NumberType.INSTANCE);
    }
}
