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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.object.SolvikAny;

/**
 * Lowers {@code throw expression} (docs/LANGUAGE_SPEC.md error-handling phases). The operand is
 * evaluated exactly once and, when it is a guest exception instance, unwinds execution with the
 * carried value inside a {@link SolvikGuestException}. A non-exception operand never reaches lowering
 * because semantic analysis rejects it, so any other runtime value would be an internal invariant
 * violation rather than a source-level error.
 */
@NodeInfo(shortName = "throw", description = "A Solvik throw statement")
public final class SolvikThrowNode extends SolvikStatementNode {

    @Child private SolvikExpressionNode value;

    public SolvikThrowNode(SolvikExpressionNode value) {
        this.value = value;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        Object operand = value.executeGeneric(frame);
        if (!(operand instanceof SolvikAny)) {
            // Semantic analysis rejects a throw whose operand is not assignable to Exception, so a
            // non-object operand here is an internal invariant violation, never source-level input.
            CompilerDirectives.shouldNotReachHere();
        }
        throw new SolvikGuestException(operand);
    }
}
