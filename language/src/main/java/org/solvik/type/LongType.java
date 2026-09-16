/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Optional;

/** The built-in signed 64-bit integer type {@code Long} (docs/LANGUAGE_SPEC.md section 4). */
public final class LongType extends Type {

    public static final LongType INSTANCE = new LongType();

    private LongType() {
        super("Long");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(NumberType.INSTANCE);
    }
}
