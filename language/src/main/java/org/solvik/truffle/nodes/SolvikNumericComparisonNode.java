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

/**
 * Solvik ordering comparisons on the numeric types other than the {@code Integer} fast path
 * (docs/LANGUAGE_SPEC.md section 4). Static analysis guarantees both operands have the same numeric
 * type, so the node compares integral values as {@code long} and floating-point values as
 * {@code double}.
 */
@NodeInfo(shortName = "numeric-compare", description = "Solvik numeric ordering comparison")
public final class SolvikNumericComparisonNode extends SolvikExpressionNode {

    public enum Op {
        LT,
        LE,
        GT,
        GE
    }

    private final Op op;
    @Child private SolvikExpressionNode left;
    @Child private SolvikExpressionNode right;

    public SolvikNumericComparisonNode(Op op, SolvikExpressionNode left, SolvikExpressionNode right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object a = left.executeGeneric(frame);
        Object b = right.executeGeneric(frame);
        if (a instanceof Byte) {
            return compare((long) ((Byte) a).byteValue(), (long) ((Byte) b).byteValue());
        }
        if (a instanceof Short) {
            return compare((long) ((Short) a).shortValue(), (long) ((Short) b).shortValue());
        }
        if (a instanceof Integer) {
            return compare((long) ((Integer) a).intValue(), (long) ((Integer) b).intValue());
        }
        if (a instanceof Long) {
            return compare(((Long) a).longValue(), ((Long) b).longValue());
        }
        if (a instanceof Float) {
            return compare((double) ((Float) a).floatValue(), (double) ((Float) b).floatValue());
        }
        if (a instanceof Double) {
            return compare(((Double) a).doubleValue(), ((Double) b).doubleValue());
        }
        throw new IllegalStateException("invalid operands reached lowered numeric comparison");
    }

    private boolean compare(long a, long b) {
        return switch (op) {
            case LT -> a < b;
            case LE -> a <= b;
            case GT -> a > b;
            case GE -> a >= b;
        };
    }

    private boolean compare(double a, double b) {
        return switch (op) {
            case LT -> a < b;
            case LE -> a <= b;
            case GT -> a > b;
            case GE -> a >= b;
        };
    }
}
