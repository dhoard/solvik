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

import org.solvik.truffle.SolvikUnit;
import org.solvik.type.AnyType;
import org.solvik.type.BooleanType;
import org.solvik.type.ByteType;
import org.solvik.type.CharacterType;
import org.solvik.type.ClassType;
import org.solvik.type.DoubleType;
import org.solvik.type.EnumType;
import org.solvik.type.FloatType;
import org.solvik.type.IntegerType;
import org.solvik.type.InterfaceType;
import org.solvik.type.LongType;
import org.solvik.type.NothingType;
import org.solvik.type.NumberType;
import org.solvik.type.RegexMatchType;
import org.solvik.type.RegexType;
import org.solvik.type.ShortType;
import org.solvik.type.StringType;
import org.solvik.type.Type;
import org.solvik.type.UnitType;

/**
 * Runtime type tests for {@code is} and {@code as} (docs/LANGUAGE_SPEC.md section 18). Static
 * analysis has already resolved the written target type, so the check is a direct predicate over the
 * runtime representation: built-in scalars by their Java representation and nominal types through
 * the runtime class hierarchy and interface set.
 *
 * <p>A {@code null} value is never an instance of the non-null target: the language rejects a
 * nullable type operand statically, and an unsuccessful cast of {@code null} is a runtime type
 * error like any other.
 */
public final class SolvikRuntimeTypes {

    private SolvikRuntimeTypes() {
    }

    /** Whether {@code value} is an instance of the non-null type {@code target}. */
    public static boolean isInstance(Object value, Type target, SolvikClass targetClass) {
        if (value == null) {
            return false;
        }
        if (target == AnyType.INSTANCE) {
            return true;
        }
        if (target == BooleanType.INSTANCE) {
            return value instanceof Boolean;
        }
        if (target == StringType.INSTANCE) {
            return value instanceof String;
        }
        if (target == CharacterType.INSTANCE) {
            return value instanceof Character;
        }
        if (target == ByteType.INSTANCE) {
            return value instanceof Byte;
        }
        if (target == ShortType.INSTANCE) {
            return value instanceof Short;
        }
        if (target == IntegerType.INSTANCE) {
            return value instanceof Integer;
        }
        if (target == LongType.INSTANCE) {
            return value instanceof Long;
        }
        if (target == FloatType.INSTANCE) {
            return value instanceof Float;
        }
        if (target == DoubleType.INSTANCE) {
            return value instanceof Double;
        }
        if (target == NumberType.INSTANCE) {
            return isNumericValue(value);
        }
        if (target == UnitType.INSTANCE) {
            return value instanceof SolvikUnit;
        }
        if (target == RegexType.INSTANCE) {
            return value instanceof SolvikRegex;
        }
        if (target == RegexMatchType.INSTANCE) {
            return value instanceof SolvikRegexMatch;
        }
        if (target == NothingType.INSTANCE) {
            return false;
        }
        if (target instanceof ClassType) {
            return value instanceof SolvikAny object && targetClass != null && object.solvikClass().isSubclassOf(targetClass);
        }
        if (target instanceof EnumType) {
            // Enum types are compared by nominal identity; generic arguments are erased, so a written
            // application still tests only the enum declaration.
            return value instanceof SolvikEnumValue enumValue && enumValue.enumClass().type() == target;
        }
        if (target instanceof InterfaceType) {
            return value instanceof SolvikAny object && object.solvikClass().implementsInterface(target.name());
        }
        return false;
    }

    private static boolean isNumericValue(Object value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double;
    }
}
