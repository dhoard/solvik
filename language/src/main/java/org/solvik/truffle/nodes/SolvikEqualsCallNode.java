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
import org.solvik.truffle.SolvikValues;

/**
 * The built-in {@code Any.equals(other: Any?): Boolean} explicit call (docs/LANGUAGE_SPEC.md
 * section 3). It shares the semantic-equality service with {@code ==}, so an explicit call and
 * the operator agree exactly, including the null precheck and user dispatch.
 *
 * <p>A safe call ({@code receiver?.equals(other)}) evaluates the receiver first: a null receiver
 * yields {@code null} and the argument is not evaluated, matching ordinary safe-call argument
 * evaluation. Otherwise both values are compared through {@link SolvikValues#equal}, which performs
 * the null precheck before any user method runs. The receiver is always the dynamic left operand.
 */
@NodeInfo(shortName = "equals", description = "Solvik universal semantic-equality call")
public final class SolvikEqualsCallNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode receiver;
    @Child private SolvikExpressionNode argument;
    private final boolean safe;

    public SolvikEqualsCallNode(SolvikExpressionNode receiver, SolvikExpressionNode argument, boolean safe) {
        this.receiver = receiver;
        this.argument = argument;
        this.safe = safe;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object left = receiver.executeGeneric(frame);
        if (left == null && safe) {
            // Ordinary safe-call evaluation: the argument is not evaluated when the receiver is null.
            return null;
        }
        Object right = argument.executeGeneric(frame);
        return SolvikValues.equal(left, right);
    }
}
