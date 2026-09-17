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
import org.solvik.truffle.SolvikException;

/**
 * Solvik unary {@code -} on the numeric types other than the {@code Int} fast path
 * (docs/LANGUAGE_SPEC.md section 4). Integral negation is checked for overflow; floating-point
 * negation follows IEEE 754.
 */
@NodeInfo(shortName = "numeric-negate", description = "Solvik numeric negation")
public final class SolvikNumericNegateNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode operand;

    public SolvikNumericNegateNode(SolvikExpressionNode operand) {
        this.operand = operand;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object value = operand.executeGeneric(frame);
        if (value instanceof Byte b) {
            if (b == Byte.MIN_VALUE) {
                throw overflow();
            }
            return (byte) -b.byteValue();
        }
        if (value instanceof Short s) {
            if (s == Short.MIN_VALUE) {
                throw overflow();
            }
            return (short) -s.shortValue();
        }
        if (value instanceof Integer i) {
            if (i == Integer.MIN_VALUE) {
                throw overflow();
            }
            return -i;
        }
        if (value instanceof Long l) {
            if (l == Long.MIN_VALUE) {
                throw overflow();
            }
            return -l;
        }
        if (value instanceof Float f) {
            return -f;
        }
        if (value instanceof Double d) {
            return -d;
        }
        throw new IllegalStateException("invalid operand reached lowered numeric unary '-'");
    }

    private SolvikException overflow() {
        return SolvikException.arithmetic("integer overflow in unary '-'", this);
    }
}
