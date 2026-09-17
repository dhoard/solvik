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
 * {@code Regex.matches(value: String): Boolean} (docs/LANGUAGE_SPEC.md section 14). The complete
 * input must match; the result is specialized as a primitive {@code boolean}. A safe call
 * ({@code receiver?.matches(...)}) evaluates the argument only for a non-null receiver and
 * otherwise yields {@code null}.
 */
@NodeInfo(shortName = ".matches", description = "Whether a Solvik Regex matches the complete input")
public final class SolvikRegexMatchesNode extends SolvikExpressionNode {

    private final boolean safe;
    @Child private SolvikExpressionNode receiver;
    @Child private SolvikExpressionNode value;

    public SolvikRegexMatchesNode(SolvikExpressionNode receiver, SolvikExpressionNode value, boolean safe) {
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
        return matches(target, frame);
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) {
        Object target = receiver.executeGeneric(frame);
        if (safe && target == null) {
            throw new IllegalStateException("a safe Regex call yielded null where a Boolean was expected");
        }
        return matches(target, frame);
    }

    private boolean matches(Object target, VirtualFrame frame) {
        return ((SolvikRegex) target).matches((String) value.executeGeneric(frame));
    }
}
