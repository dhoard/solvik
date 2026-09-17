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
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * A Solvik floating-point literal. A literal without the {@code f}/{@code F} suffix produces a
 * primitive {@code double}; the suffix narrows the same decimal text to a {@code float}.
 */
@NodeInfo(shortName = "float-literal", description = "A decimal floating-point literal")
public final class SolvikFloatingLiteralNode extends SolvikExpressionNode {

    private final boolean isFloat;
    private final double value;

    public SolvikFloatingLiteralNode(boolean isFloat, double value) {
        this.isFloat = isFloat;
        this.value = value;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return isFloat ? (float) value : value;
    }

    @Override
    public float executeFloat(VirtualFrame frame) {
        return (float) value;
    }

    @Override
    public double executeDouble(VirtualFrame frame) {
        return value;
    }
}
