/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Optional;

/** The built-in signed 16-bit integer type {@code Short} (docs/LANGUAGE_SPEC.md section 4). */
public final class ShortType extends Type {

    public static final ShortType INSTANCE = new ShortType();

    private ShortType() {
        super("Short");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(NumberType.INSTANCE);
    }
}
