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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * One lowered branch of a {@code match} (docs/LANGUAGE_SPEC.md section 12): a pattern matcher and
 * the result expression evaluated when the pattern matches. The clause is not itself an expression;
 * the enclosing {@link SolvikMatchNode} owns the branch list so patterns and results stay in source
 * order.
 */
@NodeInfo(description = "One Solvik match branch")
public final class SolvikMatchClauseNode extends Node {

    @Child private SolvikPatternNode pattern;
    @Child private SolvikExpressionNode result;

    public SolvikMatchClauseNode(SolvikPatternNode pattern, SolvikExpressionNode result) {
        this.pattern = pattern;
        this.result = result;
    }

    /** Tests the pattern against {@code value}, binding names into {@code frame} when it matches. */
    public boolean matches(VirtualFrame frame, Object value) {
        return pattern.matches(frame, value);
    }

    /** Evaluates the branch result after a successful pattern match. */
    public Object execute(VirtualFrame frame) {
        return result.executeGeneric(frame);
    }
}
