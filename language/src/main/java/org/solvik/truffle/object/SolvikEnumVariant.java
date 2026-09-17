/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.object;

import java.util.Objects;

/**
 * Runtime metadata for one declared enum variant (docs/LANGUAGE_SPEC.md section 12): its owning
 * enum, its name, and its positional value count. Variant identity is nominal, so {@code ==} on two
 * enum values compares their variants by reference.
 */
public final class SolvikEnumVariant {

    private final SolvikEnumClass owner;
    private final String name;
    private final int valueCount;

    public SolvikEnumVariant(SolvikEnumClass owner, String name, int valueCount) {
        this.owner = Objects.requireNonNull(owner);
        this.name = Objects.requireNonNull(name);
        this.valueCount = valueCount;
    }

    public SolvikEnumClass owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    /** The number of positional values this variant carries. */
    public int valueCount() {
        return valueCount;
    }

    @Override
    public String toString() {
        return owner.name() + "." + name;
    }
}
