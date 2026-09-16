/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Optional;

/**
 * The single-valued result type of a function that returns normally without a value
 * (docs/LANGUAGE_SPEC.md section 4).
 */
public final class UnitType extends Type {

    public static final UnitType INSTANCE = new UnitType();

    private UnitType() {
        super("Unit");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(ObjectType.INSTANCE);
    }
}
