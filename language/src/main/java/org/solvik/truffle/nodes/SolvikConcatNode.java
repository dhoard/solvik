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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * Solvik {@code ..} string concatenation (docs/LANGUAGE_SPEC.md section 3). Each operand is
 * rendered through {@code toString} by the lowering, so both children already produce a
 * {@code String} and concatenation is a plain Java string join.
 */
@NodeInfo(shortName = "..", description = "Solvik string concatenation")
public final class SolvikConcatNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode left;
    @Child private SolvikExpressionNode right;

    public SolvikConcatNode(SolvikExpressionNode left, SolvikExpressionNode right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return concat((String) left.executeGeneric(frame), (String) right.executeGeneric(frame));
    }

    @TruffleBoundary
    private static String concat(String left, String right) {
        return left + right;
    }
}
