/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Optional;

/**
 * The built-in {@code RegexMatch} type (docs/LANGUAGE_SPEC.md section 14). It is a non-generic
 * nominal class under {@code Object} that a {@code Regex.find} or {@code Regex.findAll} call
 * produces; it is not constructible from source. Its members are baked into static analysis:
 * immutable {@code value: String}, {@code start: Int}, {@code end: Int}, and {@code groupCount: Int}
 * properties plus {@code group(index: Int): String?}.
 */
public final class RegexMatchType extends Type {

    public static final RegexMatchType INSTANCE = new RegexMatchType();

    private RegexMatchType() {
        super("RegexMatch");
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(ObjectType.INSTANCE);
    }
}
