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

import static org.assertj.core.api.Assertions.assertThat;

import com.oracle.truffle.api.frame.VirtualFrame;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
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
        assertThat(gen.getMethod("expectInteger", Object.class)).isNotNull();
        assertThat(gen.getMethod("expectBoolean", Object.class)).isNotNull();
    }

    @Test
    public void expressionNodesExposePrimitiveExecuteMethods() throws Exception {
        Method executeInt = SolvikExpressionNode.class.getMethod("executeInt", VirtualFrame.class);
        Method executeBoolean = SolvikExpressionNode.class.getMethod("executeBoolean", VirtualFrame.class);
        assertThat(executeInt.getReturnType() == int.class).isTrue();
        assertThat(executeBoolean.getReturnType() == boolean.class).isTrue();
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
            assertThat(Modifier.isAbstract(nodeClass.getModifiers())).as(node + " must be abstract so the DSL generates its dispatch").isTrue();
            Class<?> gen = Class.forName("org.solvik.truffle.nodes." + node + "Gen");
            assertThat(gen).as(node + " must have a generated factory").isNotNull();
        }
    }
}
