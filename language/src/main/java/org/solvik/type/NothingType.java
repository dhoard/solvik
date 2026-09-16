/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

/**
 * The bottom type {@code Nothing}: it has no values and is a subtype of every type
 * (docs/LANGUAGE_SPEC.md section 4).
 */
public final class NothingType extends Type {

    public static final NothingType INSTANCE = new NothingType();

    private NothingType() {
        super("Nothing");
    }

    @Override
    public boolean isBottom() {
        return true;
    }
}
