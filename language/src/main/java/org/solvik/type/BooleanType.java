/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Optional;

/** The built-in {@code Boolean} type (docs/LANGUAGE_SPEC.md section 4). */
public final class BooleanType extends Type {

    public static final BooleanType INSTANCE = new BooleanType();

    private BooleanType() {
        super("Boolean");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(ObjectType.INSTANCE);
    }
}
