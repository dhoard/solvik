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
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * A lowered value-producing block (docs/LANGUAGE_SPEC.md section 21). Its statements execute in
 * source order in the enclosing frame, then the tail expression produces the block's value. The
 * semantic layer has already proven that every normally completing path reaches a tail result; a
 * block whose paths all transfer control never executes its tail and carries type {@code Nothing}.
 */
@NodeInfo(shortName = "block-expr", description = "A Solvik block expression")
public final class SolvikBlockExprNode extends SolvikExpressionNode {

    @Children private final SolvikStatementNode[] statements;
    @Child private SolvikExpressionNode tail;

    /**
     * @param statements the statements preceding the tail result
     * @param tail the tail expression, or {@code null} when no path completes normally
     */
    public SolvikBlockExprNode(SolvikStatementNode[] statements, SolvikExpressionNode tail) {
        this.statements = statements;
        this.tail = tail;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        for (SolvikStatementNode statement : statements) {
            statement.executeVoid(frame);
        }
        if (tail == null) {
            throw new IllegalStateException("a value-required block reached execution with no tail result");
        }
        return tail.executeGeneric(frame);
    }
}
