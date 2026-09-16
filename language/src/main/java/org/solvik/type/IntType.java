/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Optional;

/**
 * The built-in signed 32-bit integer type {@code Int} (docs/LANGUAGE_SPEC.md section 4), one of the
 * six numeric types under {@code Number}. The remaining numeric types arrive with the complete root
 * hierarchy in Phase 7.
 */
public final class IntType extends Type {

    public static final IntType INSTANCE = new IntType();

    private IntType() {
        super("Int");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(NumberType.INSTANCE);
    }
}
