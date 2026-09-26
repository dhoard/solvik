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
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.object.SolvikEnumValue;
import org.solvik.truffle.object.SolvikEnumVariant;

/**
 * Lowers the postfix propagation operator {@code expression?} (error-handling phases). The operand is
 * required by static analysis to be a {@code Result<T, E>} value: an enum with two payload-carrying
 * variants whose first is the success variant. This node evaluates the operand exactly once, returns
 * its success payload when the value is the success variant, and otherwise raises a
 * {@link SolvikPropagationException} carrying the underlying {@code Err} so it unwinds to the
 * enclosing {@code Result}-returning boundary. The success variant is fixed at lowering from the
 * compiler's closed-variant metadata, so no runtime lookup is needed and the successful path is a
 * single identity comparison plus payload read.
 */
@NodeInfo(shortName = "propagate", description = "Unwrap a Result value, propagating its Err")
public final class SolvikPropagationNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode operand;
    private final SolvikEnumVariant successVariant;

    public SolvikPropagationNode(SolvikExpressionNode operand, SolvikEnumVariant successVariant) {
        this.operand = operand;
        this.successVariant = successVariant;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object value = operand.executeGeneric(frame);
        if (value instanceof SolvikEnumValue enumValue && enumValue.variant() == successVariant) {
            return enumValue.value(0);
        }
        throw new SolvikPropagationException(value);
    }
}
