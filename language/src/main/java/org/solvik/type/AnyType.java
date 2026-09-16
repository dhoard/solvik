/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

/**
 * The top type {@code Any}: every non-null Solvik value is assignable to it, and it is not
 * assignable to any type except itself. Assigning to {@code Any} never disables static checking
 * (docs/LANGUAGE_SPEC.md sections 3 and 4).
 */
public final class AnyType extends Type {

    public static final AnyType INSTANCE = new AnyType();

    private AnyType() {
        super("Any");
    }
}
