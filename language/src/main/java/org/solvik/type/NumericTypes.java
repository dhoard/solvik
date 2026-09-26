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

import java.util.Optional;
import java.util.Set;

/**
 * Numeric-type classification for the built-in root hierarchy (docs/LANGUAGE_SPEC.md section 4).
 * The six numeric types are siblings under {@code Number}. Implicit numeric conversion is permitted
 * only where it preserves both integral range and representable precision; such a conversion is the
 * {@linkplain #widens(Type, Type) widening} relation defined here. Everything else (narrowing, and
 * any widening that would drop bits, such as {@code Integer -> Float} or {@code Long -> Double}) is
 * not a relation between the types and requires an explicit {@code T(value)} conversion.
 *
 * <p>Widening is deliberately not modeled as a subtype edge: numeric types remain siblings and
 * nominal assignability is unchanged. The relations here are consulted only at coercion and
 * arithmetic sites by the semantic analyzer, which records a compiler-inserted conversion.
 */
public final class NumericTypes {

    private static final Set<Type> NUMERIC = Set.of(
                    ByteType.INSTANCE, ShortType.INSTANCE, IntegerType.INSTANCE, LongType.INSTANCE, FloatType.INSTANCE, DoubleType.INSTANCE);

    /** Integral numeric types whose arithmetic is checked for overflow. */
    private static final Set<Type> INTEGRAL = Set.of(
                    ByteType.INSTANCE, ShortType.INSTANCE, IntegerType.INSTANCE, LongType.INSTANCE);

    private NumericTypes() {
    }

    /** Whether {@code type} is one of the six concrete numeric types (not {@code Number} itself). */
    public static boolean isNumeric(Type type) {
        return type != null && NUMERIC.contains(type);
    }

    /** Whether {@code type} is {@code Byte}, {@code Short}, {@code Integer}, or {@code Long}. */
    public static boolean isIntegral(Type type) {
        return type != null && INTEGRAL.contains(type);
    }

    /** Whether {@code type} is {@code Float} or {@code Double}. */
    public static boolean isFloating(Type type) {
        return type == FloatType.INSTANCE || type == DoubleType.INSTANCE;
    }

    /**
     * Whether {@code from} is a strict widening of {@code to}: a value of {@code from} maps to
     * {@code to} with no loss of integral range and no loss of representable precision
     * (docs/LANGUAGE_SPEC.md section 4). The relation holds exactly for:
     * <ul>
     * <li>integral to integral along {@code Byte -> Short -> Integer -> Long};</li>
     * <li>{@code Byte} or {@code Short} to {@code Float} (at most 15 value bits, within the 24-bit
     *     {@code Float} significand) and to {@code Double};</li>
     * <li>{@code Integer} to {@code Double} (32 bits within the 53-bit significand), but not to
     *     {@code Float};</li>
     * <li>{@code Float} to {@code Double}.</li>
     * </ul>
     * Notably {@code Long} widens to neither {@code Float} nor {@code Double} (64 bits exceed the
     * 53-bit significand), and no narrowing ever widens. A type never widens to itself; same-type
     * compatibility is nominal assignability, handled separately.
     */
    public static boolean widens(Type from, Type to) {
        if (from == null || to == null || !isNumeric(from) || !isNumeric(to) || from == to) {
            return false;
        }
        if (isIntegral(from)) {
            if (isIntegral(to)) {
                return integralRank(from) < integralRank(to);
            }
            // Integral -> floating: only when every value of `from` is exactly representable.
            if (to == FloatType.INSTANCE) {
                return from == ByteType.INSTANCE || from == ShortType.INSTANCE;
            }
            return from == ByteType.INSTANCE || from == ShortType.INSTANCE || from == IntegerType.INSTANCE;
        }
        if (from == FloatType.INSTANCE) {
            return to == DoubleType.INSTANCE;
        }
        // Double is the widest floating type and widens to nothing.
        return false;
    }

    /** Whether {@code from} is {@code to} itself or widens to it. */
    public static boolean widensOrSame(Type from, Type to) {
        return from == to || widens(from, to);
    }

    /**
     * The least common widened numeric type of {@code a} and {@code b}: the unique minimal numeric
     * type that both operands widen-or-stay to, or empty when no such type exists (for example
     * {@code Long} and {@code Float}). Used to type mixed arithmetic, ordering, and equality operands.
     *
     * <p>The {@code widens} relation has a unique minimal common element for every pair that has any
     * common widened type, so scanning the most-specific-to-widest ordering and returning the first
     * candidate that both operands admit yields that minimum.
     */
    public static Optional<Type> leastCommonNumeric(Type a, Type b) {
        if (!isNumeric(a) || !isNumeric(b)) {
            return Optional.empty();
        }
        for (Type candidate : NUMERIC_ORDER) {
            if (widensOrSame(a, candidate) && widensOrSame(b, candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Numeric types ordered most-specific-to-widest, with {@code Float} placed before {@code Long}
     * because {@code Float -> Long} is not a widening (so the two are incomparable and the scan
     * never sees them as a common widened type of each other).
     */
    private static final Type[] NUMERIC_ORDER = {
                    ByteType.INSTANCE, ShortType.INSTANCE, IntegerType.INSTANCE, FloatType.INSTANCE, LongType.INSTANCE, DoubleType.INSTANCE};

    /** Strict integral width ordering; only defined for integral types. */
    private static int integralRank(Type integral) {
        if (integral == ByteType.INSTANCE) {
            return 0;
        }
        if (integral == ShortType.INSTANCE) {
            return 1;
        }
        if (integral == IntegerType.INSTANCE) {
            return 2;
        }
        return 3;
    }
}
