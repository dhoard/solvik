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
import org.solvik.truffle.object.SolvikRegex;

/**
 * {@code Regex.findAll(value: String): List<RegexMatch>} (docs/LANGUAGE_SPEC.md section 14).
 * Returns every non-overlapping match from left to right as the built-in immutable list. A safe call
 * ({@code receiver?.findAll(...)}) evaluates the argument only for a non-null receiver.
 */
@NodeInfo(shortName = ".findAll", description = "All Solvik Regex matches")
public final class SolvikRegexFindAllNode extends SolvikExpressionNode {

    private final boolean safe;
    @Child private SolvikExpressionNode receiver;
    @Child private SolvikExpressionNode value;

    public SolvikRegexFindAllNode(SolvikExpressionNode receiver, SolvikExpressionNode value, boolean safe) {
        this.receiver = receiver;
        this.value = value;
        this.safe = safe;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object target = receiver.executeGeneric(frame);
        if (safe && target == null) {
            return null;
        }
        return ((SolvikRegex) target).findAll((String) value.executeGeneric(frame));
    }
}
