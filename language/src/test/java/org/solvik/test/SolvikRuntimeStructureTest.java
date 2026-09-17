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
