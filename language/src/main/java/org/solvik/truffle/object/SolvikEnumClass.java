/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.object;

import java.util.Objects;
import org.solvik.type.EnumType;

/**
 * Runtime metadata for a Solvik enum (docs/LANGUAGE_SPEC.md section 12). It carries the enum's
 * identity, the compiler's nominal {@link EnumType} (used by {@code is}/{@code as} tests because
 * generic arguments are erased), and its name for display.
 *
 * <p>Instances are created once per lowered enum declaration. The complete variant set lives in the
 * compiler's {@code EnumSymbol}; runtime construction is statically resolved and passes the selected
 * variant directly, so no runtime name lookup is needed.
 */
public final class SolvikEnumClass {

    private final String name;
    private final EnumType type;

    public SolvikEnumClass(String name, EnumType type) {
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
    }

    public String name() {
        return name;
    }

    /** The compile-time nominal type of this enum, compared by identity in runtime type tests. */
    public EnumType type() {
        return type;
    }

    @Override
    public String toString() {
        return name;
    }
}
