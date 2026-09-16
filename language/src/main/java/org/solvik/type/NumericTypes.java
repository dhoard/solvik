/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Set;

/**
 * Numeric-type classification for the built-in root hierarchy (docs/LANGUAGE_SPEC.md section 4).
 * The six numeric types are siblings under {@code Number}; arithmetic and ordering require both
 * operands to have the same numeric type, and no implicit widening or narrowing is permitted.
 */
public final class NumericTypes {

    private static final Set<Type> NUMERIC = Set.of(
                    ByteType.INSTANCE, ShortType.INSTANCE, IntType.INSTANCE, LongType.INSTANCE, FloatType.INSTANCE, DoubleType.INSTANCE);

    /** Integral numeric types whose arithmetic is checked for overflow. */
    private static final Set<Type> INTEGRAL = Set.of(
                    ByteType.INSTANCE, ShortType.INSTANCE, IntType.INSTANCE, LongType.INSTANCE);

    private NumericTypes() {
    }

    /** Whether {@code type} is one of the six concrete numeric types (not {@code Number} itself). */
    public static boolean isNumeric(Type type) {
        return type != null && NUMERIC.contains(type);
    }

    /** Whether {@code type} is {@code Byte}, {@code Short}, {@code Int}, or {@code Long}. */
    public static boolean isIntegral(Type type) {
        return type != null && INTEGRAL.contains(type);
    }

    /** Whether {@code type} is {@code Float} or {@code Double}. */
    public static boolean isFloating(Type type) {
        return type == FloatType.INSTANCE || type == DoubleType.INSTANCE;
    }
}
