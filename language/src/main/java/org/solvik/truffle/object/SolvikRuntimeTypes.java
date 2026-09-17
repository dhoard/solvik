/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.object;

import org.solvik.truffle.SolvikUnit;
import org.solvik.type.AnyType;
import org.solvik.type.BooleanType;
import org.solvik.type.ByteType;
import org.solvik.type.CharType;
import org.solvik.type.ClassType;
import org.solvik.type.DoubleType;
import org.solvik.type.EnumType;
import org.solvik.type.FloatType;
import org.solvik.type.IntType;
import org.solvik.type.InterfaceType;
import org.solvik.type.LongType;
import org.solvik.type.NothingType;
import org.solvik.type.NumberType;
import org.solvik.type.ObjectType;
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
        if (target == AnyType.INSTANCE || target == ObjectType.INSTANCE) {
            return true;
        }
        if (target == BooleanType.INSTANCE) {
            return value instanceof Boolean;
        }
        if (target == StringType.INSTANCE) {
            return value instanceof String;
        }
        if (target == CharType.INSTANCE) {
            return value instanceof Character;
        }
        if (target == ByteType.INSTANCE) {
            return value instanceof Byte;
        }
        if (target == ShortType.INSTANCE) {
            return value instanceof Short;
        }
        if (target == IntType.INSTANCE) {
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
        if (target == NothingType.INSTANCE) {
            return false;
        }
        if (target instanceof ClassType) {
            return value instanceof SolvikObject object && targetClass != null && object.solvikClass().isSubclassOf(targetClass);
        }
        if (target instanceof EnumType) {
            // Enum types are compared by nominal identity; generic arguments are erased, so a written
            // application still tests only the enum declaration.
            return value instanceof SolvikEnumValue enumValue && enumValue.enumClass().type() == target;
        }
        if (target instanceof InterfaceType) {
            return value instanceof SolvikObject object && object.solvikClass().implementsInterface(target.name());
        }
        return false;
    }

    private static boolean isNumericValue(Object value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float || value instanceof Double;
    }
}
