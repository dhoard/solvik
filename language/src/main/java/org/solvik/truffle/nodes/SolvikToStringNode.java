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
import org.solvik.truffle.SolvikDisplay;
import org.solvik.truffle.SolvikFunction;
import org.solvik.truffle.object.SolvikObject;

/**
 * The built-in {@code Any.toString()}: {@code String} representation of any Solvik value
 * (docs/LANGUAGE_SPEC.md sections 4 and 6).
 *
 * <p>Null renders as {@code "null"}. A user object whose runtime method table supplies
 * {@code toString} (its own override or an inherited one) dispatches to that method, so an override
 * reaches every call site, including a call through an {@code Any}-typed receiver, {@code print},
 * and {@code ..}. Every other value uses the built-in scalar representation from
 * {@link SolvikDisplay}. A safe call on a null receiver yields {@code null} instead of
 * {@code "null"}.
 */
@NodeInfo(shortName = "toString", description = "Renders any Solvik value as a String")
public final class SolvikToStringNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode operand;
    private final boolean safe;

    public SolvikToStringNode(SolvikExpressionNode operand, boolean safe) {
        this.operand = operand;
        this.safe = safe;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object value = operand.executeGeneric(frame);
        if (value == null) {
            return safe ? null : "null";
        }
        if (value instanceof SolvikObject object) {
            SolvikFunction override = object.solvikClass().method("toString");
            if (override != null) {
                return override.callTarget().call(object);
            }
        }
        return SolvikDisplay.render(value);
    }
}
