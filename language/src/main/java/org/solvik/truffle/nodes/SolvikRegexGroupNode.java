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
import org.solvik.truffle.object.SolvikRegexMatch;

/**
 * {@code RegexMatch.group(index: Integer): String?} (docs/LANGUAGE_SPEC.md section 14). Group zero is
 * the complete match; a group that did not participate yields {@code null}. An out-of-range index
 * raises a Solvik runtime bounds error. A safe call ({@code receiver?.group(...)}) evaluates the
 * index only for a non-null receiver.
 */
@NodeInfo(shortName = ".group", description = "Read a Solvik RegexMatch capture group")
public final class SolvikRegexGroupNode extends SolvikExpressionNode {

    private final boolean safe;
    @Child private SolvikExpressionNode receiver;
    @Child private SolvikExpressionNode index;

    public SolvikRegexGroupNode(SolvikExpressionNode receiver, SolvikExpressionNode index, boolean safe) {
        this.receiver = receiver;
        this.index = index;
        this.safe = safe;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object target = receiver.executeGeneric(frame);
        if (safe && target == null) {
            return null;
        }
        return ((SolvikRegexMatch) target).group(index.executeInt(frame), this);
    }
}
