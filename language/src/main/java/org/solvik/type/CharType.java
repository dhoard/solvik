/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Optional;

/**
 * The built-in {@code Char} type, a single Unicode scalar value (docs/LANGUAGE_SPEC.md sections 1
 * and 4). {@code Char} is not a numeric type: it has its own literals, compares by value, and
 * displays as its contents.
 */
public final class CharType extends Type {

    public static final CharType INSTANCE = new CharType();

    private CharType() {
        super("Char");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(ObjectType.INSTANCE);
    }
}
