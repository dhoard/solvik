/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Optional;

/**
 * The root of class, interface, and enum values (docs/LANGUAGE_SPEC.md section 4). The built-in
 * scalar types are its direct subtypes; user-defined classes join the hierarchy in later phases.
 */
public final class ObjectType extends Type {

    public static final ObjectType INSTANCE = new ObjectType();

    private ObjectType() {
        super("Object");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(AnyType.INSTANCE);
    }
}
