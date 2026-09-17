/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Optional;

/**
 * The built-in {@code Regex} type (docs/LANGUAGE_SPEC.md section 14). It is a non-generic nominal
 * class under {@code Object} whose members are baked into static analysis rather than declared in
 * source: {@code matches}, {@code find}, {@code findAll}, and {@code replace}. A value is produced
 * only by the built-in constructor {@code Regex(pattern)}, so the type has no declared
 * constructor symbol and no source-visible property storage.
 */
public final class RegexType extends Type {

    public static final RegexType INSTANCE = new RegexType();

    private RegexType() {
        super("Regex");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(ObjectType.INSTANCE);
    }
}
