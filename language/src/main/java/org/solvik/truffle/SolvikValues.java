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
package org.solvik.truffle;

/**
 * The single definition of Solvik {@code ==} for the erased runtime (docs/LANGUAGE_SPEC.md section 3).
 * Built-in scalars compare by value; ordinary class instances compare by identity. Collection
 * members use this so {@code Set} and {@code Map} agree with the expression equality rule rather
 * than delegating to arbitrary Java {@code equals}.
 */
public final class SolvikValues {

    private SolvikValues() {
    }

    /** Whether {@code left} equals {@code right} under the specification's Solvik equality rule. */
    public static boolean equal(Object left, Object right) {
        // Floating-point equality is decided by IEEE 754 value comparison before the identity
        // shortcut: a NaN never equals itself, so two references to the same boxed NaN are not
        // equal (docs/LANGUAGE_SPEC.md sections 3 and 4).
        if (left instanceof Float a && right instanceof Float b) {
            return a.floatValue() == b.floatValue();
        }
        if (left instanceof Double a && right instanceof Double b) {
            return a.doubleValue() == b.doubleValue();
        }
        if (left == right) {
            return true;
        }
        if (left instanceof org.solvik.truffle.object.SolvikEnumValue a && right instanceof org.solvik.truffle.object.SolvikEnumValue b) {
            // Enum values compare by value, not identity (docs/LANGUAGE_SPEC.md section 3).
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
        if (left instanceof Character a && right instanceof Character b) {
            return a.charValue() == b.charValue();
        }
        // Ordinary class instances compare by identity in Solvik; the reference check above failed.
        return false;
    }
}
