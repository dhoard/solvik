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
package org.solvik.truffle.object;

import java.util.Objects;

/**
 * A Solvik enum value (docs/LANGUAGE_SPEC.md section 12): a variant plus its positional values. The
 * runtime representation is an immutable variant reference and a private copy of the values, so an
 * enum value cannot be mutated through the runtime API.
 *
 * <p>The specification compares enum values by value (section 3): two values are equal when they
 * carry the same variant and pairwise equal values, where scalar and nested enum values compare by
 * value and ordinary class instances by identity.
 */
public final class SolvikEnumValue {

    private final SolvikEnumVariant variant;
    private final Object[] values;

    public SolvikEnumValue(SolvikEnumVariant variant, Object[] values) {
        this.variant = Objects.requireNonNull(variant);
        this.values = values.clone();
    }

    public SolvikEnumVariant variant() {
        return variant;
    }

    public SolvikEnumClass enumClass() {
        return variant.owner();
    }

    public int valueCount() {
        return values.length;
    }

    /** The positional value at {@code index}; match destructuring uses this in a later phase. */
    public Object value(int index) {
        return values[index];
    }

    /** Whether this value equals {@code other} by value under the specification's enum rule. */
    public boolean valueEquals(SolvikEnumValue other) {
        if (variant != other.variant) {
            return false;
        }
        for (int i = 0; i < values.length; i++) {
            if (!valuesEqual(values[i], other.values[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Solvik equality for one enum value slot: scalars and nested enum values compare by value and
     * every other object compares by identity. This mirrors {@code SolvikEqualNode} so that enum
     * equality and {@code ==} agree.
     */
    public static boolean valuesEqual(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left instanceof SolvikEnumValue a && right instanceof SolvikEnumValue b) {
            return a.valueEquals(b);
        }
        if (left instanceof Integer a && right instanceof Integer b) {
            return a.intValue() == b.intValue();
        }
        if (left instanceof Boolean a && right instanceof Boolean b) {
            return a.booleanValue() == b.booleanValue();
        }
        if (left instanceof String a && right instanceof String b) {
            return a.equals(b);
        }
        if (left instanceof Byte a && right instanceof Byte b) {
            return a.byteValue() == b.byteValue();
        }
        if (left instanceof Short a && right instanceof Short b) {
            return a.shortValue() == b.shortValue();
        }
        if (left instanceof Long a && right instanceof Long b) {
            return a.longValue() == b.longValue();
        }
        if (left instanceof Float a && right instanceof Float b) {
            return a.floatValue() == b.floatValue();
        }
        if (left instanceof Double a && right instanceof Double b) {
            return a.doubleValue() == b.doubleValue();
        }
        if (left instanceof Character a && right instanceof Character b) {
            return a.charValue() == b.charValue();
        }
        // Ordinary class instances compare by identity in Solvik; the reference check above failed.
        return false;
    }

    @Override
    public String toString() {
        return variant.toString();
    }
}
