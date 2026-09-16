/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import org.solvik.type.AnyType;
import org.solvik.type.BooleanType;
import org.solvik.type.ByteType;
import org.solvik.type.CharType;
import org.solvik.type.DoubleType;
import org.solvik.type.FloatType;
import org.solvik.type.FunctionType;
import org.solvik.type.IntType;
import org.solvik.type.LongType;
import org.solvik.type.NothingType;
import org.solvik.type.NumberType;
import org.solvik.type.NumericTypes;
import org.solvik.type.ObjectType;
import org.solvik.type.ShortType;
import org.solvik.type.StringType;
import org.solvik.type.Type;
import org.solvik.type.TypeEnvironment;
import org.solvik.type.UnitType;

/** Phase 4 tests for the explicit compiler type model and its built-in hierarchy metadata. */
public final class SolvikTypeModelTest {

    @Test
    public void allBuiltinsResolveByName() {
        TypeEnvironment environment = new TypeEnvironment();
        assertEquals(AnyType.INSTANCE, environment.resolve("Any").orElseThrow());
        assertEquals(ObjectType.INSTANCE, environment.resolve("Object").orElseThrow());
        assertEquals(NothingType.INSTANCE, environment.resolve("Nothing").orElseThrow());
        assertEquals(UnitType.INSTANCE, environment.resolve("Unit").orElseThrow());
        assertEquals(BooleanType.INSTANCE, environment.resolve("Boolean").orElseThrow());
        assertEquals(StringType.INSTANCE, environment.resolve("String").orElseThrow());
        assertEquals(IntType.INSTANCE, environment.resolve("Int").orElseThrow());
        assertEquals(NumberType.INSTANCE, environment.resolve("Number").orElseThrow());
        assertEquals(ByteType.INSTANCE, environment.resolve("Byte").orElseThrow());
        assertEquals(ShortType.INSTANCE, environment.resolve("Short").orElseThrow());
        assertEquals(LongType.INSTANCE, environment.resolve("Long").orElseThrow());
        assertEquals(FloatType.INSTANCE, environment.resolve("Float").orElseThrow());
        assertEquals(DoubleType.INSTANCE, environment.resolve("Double").orElseThrow());
        assertEquals(CharType.INSTANCE, environment.resolve("Char").orElseThrow());
        assertTrue(environment.resolve("Widget").isEmpty());
        assertEquals(List.of("Any", "Object", "Nothing", "Number", "Byte", "Short", "Int", "Long", "Float", "Double", "Boolean", "Char", "String", "Unit"), //
                environment.builtins().stream().map(Type::name).toList());
    }

    @Test
    public void builtinsFormTheSpecifiedInitialHierarchy() {
        assertTrue(IntType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE));
        assertTrue(IntType.INSTANCE.isSubtypeOf(AnyType.INSTANCE));
        assertTrue(BooleanType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE));
        assertTrue(StringType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE));
        assertTrue(UnitType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE));
        assertTrue(ObjectType.INSTANCE.isSubtypeOf(AnyType.INSTANCE));
        // Reflexive.
        assertTrue(IntType.INSTANCE.isSubtypeOf(IntType.INSTANCE));
        // Unrelated nominal types are not compatible.
        assertFalse(IntType.INSTANCE.isSubtypeOf(StringType.INSTANCE));
        assertFalse(StringType.INSTANCE.isSubtypeOf(IntType.INSTANCE));
        // Any is the top type but is not itself an Object.
        assertFalse(AnyType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE));
    }

    @Test
    public void completeNumericHierarchyIsPresent() {
        for (Type numeric : List.<Type>of(ByteType.INSTANCE, ShortType.INSTANCE, IntType.INSTANCE, LongType.INSTANCE, FloatType.INSTANCE, DoubleType.INSTANCE)) {
            assertTrue(numeric.name() + " must derive from Number", numeric.isSubtypeOf(NumberType.INSTANCE));
            assertTrue(numeric.name() + " must derive from Object", numeric.isSubtypeOf(ObjectType.INSTANCE));
            assertTrue(numeric.name() + " must be numeric", NumericTypes.isNumeric(numeric));
        }
        assertTrue(NumberType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE));
        assertFalse("Number itself has no values and is not a concrete numeric type", NumericTypes.isNumeric(NumberType.INSTANCE));
        assertFalse("Char is not a numeric type", NumericTypes.isNumeric(CharType.INSTANCE));
        assertTrue(CharType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE));
        // Numeric siblings are not assignment-compatible with one another.
        assertFalse(IntType.INSTANCE.isAssignableTo(LongType.INSTANCE));
        assertFalse(LongType.INSTANCE.isAssignableTo(IntType.INSTANCE));
        assertFalse(IntType.INSTANCE.isAssignableTo(DoubleType.INSTANCE));
        // Integral classification.
        assertTrue(NumericTypes.isIntegral(ByteType.INSTANCE));
        assertTrue(NumericTypes.isIntegral(ShortType.INSTANCE));
        assertTrue(NumericTypes.isIntegral(IntType.INSTANCE));
        assertTrue(NumericTypes.isIntegral(LongType.INSTANCE));
        assertFalse(NumericTypes.isIntegral(FloatType.INSTANCE));
        assertFalse(NumericTypes.isIntegral(DoubleType.INSTANCE));
        assertTrue(NumericTypes.isFloating(FloatType.INSTANCE));
        assertTrue(NumericTypes.isFloating(DoubleType.INSTANCE));
    }

    @Test
    public void nothingIsTheBottomType() {
        for (Type type : List.<Type>of(AnyType.INSTANCE, ObjectType.INSTANCE, UnitType.INSTANCE, //
                BooleanType.INSTANCE, StringType.INSTANCE, IntType.INSTANCE, NumberType.INSTANCE, //
                ByteType.INSTANCE, ShortType.INSTANCE, LongType.INSTANCE, FloatType.INSTANCE, //
                DoubleType.INSTANCE, CharType.INSTANCE)) {
            assertTrue("Nothing must be assignable to " + type.name(), NothingType.INSTANCE.isAssignableTo(type));
        }
        assertTrue(NothingType.INSTANCE.isBottom());
        assertFalse(AnyType.INSTANCE.isBottom());
    }

    @Test
    public void functionTypeCarriesParameterAndReturnTypes() {
        FunctionType type = new FunctionType(List.of(IntType.INSTANCE, StringType.INSTANCE), BooleanType.INSTANCE);
        assertEquals(List.of(IntType.INSTANCE, StringType.INSTANCE), type.parameterTypes());
        assertEquals(BooleanType.INSTANCE, type.returnType());
        assertEquals("(Int, String) -> Boolean", type.name());
        assertTrue(type.isSubtypeOf(type));
        FunctionType other = new FunctionType(List.of(IntType.INSTANCE, StringType.INSTANCE), BooleanType.INSTANCE);
        assertFalse("distinct function types are distinct identities", type.isSubtypeOf(other));
    }
}
