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
 * Solvik null coalescing {@code left ?? right} (docs/LANGUAGE_SPEC.md section 3). The right operand
 * is evaluated only when the left is {@code null}, so the operator short-circuits exactly like the
 * specification requires. Static analysis guarantees the left operand is nullable.
 */
@NodeInfo(shortName = "??", description = "Solvik null coalescing")
public final class SolvikCoalesceNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode left;
    @Child private SolvikExpressionNode right;

    public SolvikCoalesceNode(SolvikExpressionNode left, SolvikExpressionNode right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object value = left.executeGeneric(frame);
        return value != null ? value : right.executeGeneric(frame);
    }
}
