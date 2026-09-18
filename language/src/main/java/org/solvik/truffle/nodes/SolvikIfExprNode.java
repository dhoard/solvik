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

/**
 * A lowered {@code if} expression (docs/LANGUAGE_SPEC.md section 21). The condition is evaluated
 * exactly once and only the selected branch executes, producing its result value. Static analysis
 * has already guaranteed the condition is {@code Boolean} and that an {@code else} path exists, so
 * the missing-{@code else} path is unreachable for a well-typed program.
 */
@NodeInfo(shortName = "if-expr", description = "A Solvik if expression")
public final class SolvikIfExprNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode condition;
    @Child private SolvikExpressionNode thenValue;
    @Child private SolvikExpressionNode elseValue;

    public SolvikIfExprNode(SolvikExpressionNode condition, SolvikExpressionNode thenValue, SolvikExpressionNode elseValue) {
        this.condition = condition;
        this.thenValue = thenValue;
        this.elseValue = elseValue;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        if (condition.executeBoolean(frame)) {
            return thenValue.executeGeneric(frame);
        }
        if (elseValue == null) {
            throw new IllegalStateException("an expression if reached execution without an else branch");
        }
        return elseValue.executeGeneric(frame);
    }
}
