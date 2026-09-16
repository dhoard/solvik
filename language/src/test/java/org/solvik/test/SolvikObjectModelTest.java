/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import com.oracle.truffle.api.object.DynamicObject.PutNode;
import com.oracle.truffle.api.strings.TruffleString;
import org.junit.Test;
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikObject;

/**
 * Object-model structure tests (docs/TEST_PLAN.md "Truffle Runtime"): the class layout is fixed,
 * instances of one class share a stable shape, and there is no API for inserting undeclared
 * members. The shape-sharing assertions exercise the same allocation path used by
 * {@code SolvikNewNode}.
 */
public final class SolvikObjectModelTest {

    private static SolvikObject newInstance(SolvikClass solvikClass, PutNode putNode) {
        SolvikObject object = new SolvikObject(solvikClass);
        for (int i = 0; i < solvikClass.propertyCount(); i++) {
            putNode.execute(object, solvikClass.propertyKey(i), null);
        }
        return object;
    }

    @Test
    public void instancesOfAClassShareAStableShape() {
        SolvikClass point = new SolvikClass("Point", List.of("x", "y"), List.of(false, false));
        PutNode putNode = PutNode.create();
        SolvikObject a = newInstance(point, putNode);
        SolvikObject b = newInstance(point, putNode);
        assertSame("same class and property order must reuse one shape", a.getShape(), b.getShape());
        assertEquals(2, a.getShape().getPropertyCount());
        assertEquals(0, SolvikClass.rootShape().getPropertyCount());
    }

    @Test
    public void propertyMetadataIsFixedByTheClass() {
        SolvikClass cell = new SolvikClass("Cell", List.of("value", "label"), List.of(true, false));
        assertEquals("Cell", cell.name());
        assertEquals(2, cell.propertyCount());
        assertEquals("value", cell.propertyName(0));
        assertEquals(0, cell.propertyIndex("value"));
        assertEquals(1, cell.propertyIndex("label"));
        assertTrue(cell.isPropertyMutable(0));
        assertFalse(cell.isPropertyMutable(1));
        assertEquals("label", cell.propertyKey(1).toJavaStringUncached());
    }

    @Test
    public void declaredPropertiesExistAfterAllocation() {
        SolvikClass marker = new SolvikClass("Marker", List.of("id"), List.of(false));
        PutNode putNode = PutNode.create();
        SolvikObject object = newInstance(marker, putNode);
        assertEquals(1, object.getShape().getPropertyCount());
        assertTrue(object.getShape().hasProperty(marker.propertyKey(0)));
        TruffleString missing = TruffleString.fromJavaStringUncached("missing", TruffleString.Encoding.UTF_8);
        assertFalse("no undeclared member is present", object.getShape().hasProperty(missing));
    }
}
