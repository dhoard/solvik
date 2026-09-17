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
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikFunction;

/**
 * Runs the immediate superclass constructor as the first step of a subclass constructor
 * (docs/LANGUAGE_SPEC.md section 7). The subclass {@code this} is threaded through as the hidden
 * first frame argument so the superclass constructor initializes the same instance.
 */
@NodeInfo(shortName = "super-constructor", description = "Run the immediate superclass constructor")
public final class SolvikSuperConstructorNode extends SolvikStatementNode {

    private final SolvikFunction superConstructor;
    private final int thisSlot;
    @Children private final SolvikExpressionNode[] arguments;

    public SolvikSuperConstructorNode(SolvikFunction superConstructor, int thisSlot, SolvikExpressionNode[] arguments) {
        this.superConstructor = superConstructor;
        this.thisSlot = thisSlot;
        this.arguments = arguments;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        Object[] callArguments = new Object[arguments.length + 1];
        callArguments[0] = frame.getValue(thisSlot);
        for (int i = 0; i < arguments.length; i++) {
            callArguments[i + 1] = arguments[i].executeGeneric(frame);
        }
        superConstructor.callTarget().call(callArguments);
    }
}
