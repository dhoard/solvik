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
 * Solvik {@code ===}. It answers whether two values are the same guest reference
 * (docs/LANGUAGE_SPEC.md section 3): a reference comparison only, never {@code equals}, a guest
 * method, or {@code Object.equals}.
 *
 * <p>Static analysis rejects {@code Any}/{@code Object}, scalars, enums, regex, {@code Unit}, and
 * unbounded type parameters before lowering, so each runtime operand is either {@code null} or a
 * reference-bearing allocation. The node therefore compares the two executed references directly.
 */
@NodeInfo(shortName = "===", description = "Solvik allocation-identity comparison")
public final class SolvikIdentityNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode left;
    @Child private SolvikExpressionNode right;

    public SolvikIdentityNode(SolvikExpressionNode left, SolvikExpressionNode right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return executeBoolean(frame);
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) {
        // Both operands are evaluated to guest values; the comparison is pure reference identity.
        return left.executeGeneric(frame) == right.executeGeneric(frame);
    }
}
