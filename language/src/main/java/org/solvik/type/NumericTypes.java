/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
