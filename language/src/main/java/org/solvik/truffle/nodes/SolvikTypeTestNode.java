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
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikRuntimeTypes;
import org.solvik.type.Type;

/**
 * A Solvik type test {@code value is T} (docs/LANGUAGE_SPEC.md section 18). The written target type
 * was resolved statically and is checked against the runtime representation; the result is always a
 * {@code Boolean}.
 */
@NodeInfo(shortName = "is", description = "A Solvik type test")
public final class SolvikTypeTestNode extends SolvikExpressionNode {

    private final Type target;
    private final SolvikClass targetClass;
    @Child private SolvikExpressionNode operand;

    public SolvikTypeTestNode(Type target, SolvikClass targetClass, SolvikExpressionNode operand) {
        this.target = target;
        this.targetClass = targetClass;
        this.operand = operand;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return SolvikRuntimeTypes.isInstance(operand.executeGeneric(frame), target, targetClass);
    }
}
