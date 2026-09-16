/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Optional;

/** The built-in {@code String} type (docs/LANGUAGE_SPEC.md sections 4 and 15). */
public final class StringType extends Type {

    public static final StringType INSTANCE = new StringType();

    private StringType() {
        super("String");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(ObjectType.INSTANCE);
    }
}
