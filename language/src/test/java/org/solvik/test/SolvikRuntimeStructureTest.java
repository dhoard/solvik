/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import com.oracle.truffle.api.frame.VirtualFrame;
import org.junit.Test;
import org.solvik.truffle.nodes.SolvikExpressionNode;

/**
 * Structural checks that the Phase 5 Truffle backend keeps primitive specializations: the Truffle
 * DSL type system exposes primitive expectation helpers, expression nodes expose primitive execute
 * methods, and each arithmetic/comparison node has a generated factory produced from its
 * {@code @Specialization} declarations.
 */
public final class SolvikRuntimeStructureTest {

    @Test
    public void dslTypeSystemExposesPrimitiveHelpers() throws Exception {
        Class<?> gen = Class.forName("org.solvik.truffle.SolvikTypesGen");
        assertNotNull(gen.getMethod("expectInteger", Object.class));
        assertNotNull(gen.getMethod("expectBoolean", Object.class));
    }

    @Test
    public void expressionNodesExposePrimitiveExecuteMethods() throws Exception {
        Method executeInt = SolvikExpressionNode.class.getMethod("executeInt", VirtualFrame.class);
        Method executeBoolean = SolvikExpressionNode.class.getMethod("executeBoolean", VirtualFrame.class);
        assertTrue(executeInt.getReturnType() == int.class);
        assertTrue(executeBoolean.getReturnType() == boolean.class);
    }

    @Test
    public void arithmeticAndComparisonNodesHaveGeneratedFactories() throws Exception {
        String[] nodes = {
                        "SolvikAddNode", "SolvikSubNode", "SolvikMulNode", "SolvikDivNode",
                        "SolvikLessThanNode", "SolvikLessOrEqualNode", "SolvikGreaterThanNode", "SolvikGreaterOrEqualNode",
                        "SolvikEqualNode", "SolvikLogicalNotNode", "SolvikNegateNode",
                        "SolvikReadLocalVariableNode", "SolvikWriteLocalVariableNode"};
        for (String node : nodes) {
            Class<?> nodeClass = Class.forName("org.solvik.truffle.nodes." + node);
            assertTrue(node + " must be abstract so the DSL generates its dispatch", Modifier.isAbstract(nodeClass.getModifiers()));
            Class<?> gen = Class.forName("org.solvik.truffle.nodes." + node + "Gen");
            assertNotNull(node + " must have a generated factory", gen);
        }
    }
}
