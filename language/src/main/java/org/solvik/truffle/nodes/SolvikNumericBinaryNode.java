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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikException;

/**
 * Solvik arithmetic on the numeric types other than the {@code Int} fast path: {@code Byte},
 * {@code Short}, {@code Long}, {@code Float}, and {@code Double} (docs/LANGUAGE_SPEC.md section 4).
 * Static analysis guarantees both operands have the same numeric type, so the node dispatches on
 * the left operand's runtime representation. Integral arithmetic is checked and raises a Solvik
 * runtime arithmetic error on overflow or division by zero; floating-point arithmetic follows
 * IEEE 754.
 */
@NodeInfo(shortName = "numeric-op", description = "Solvik numeric arithmetic on non-Int types")
public final class SolvikNumericBinaryNode extends SolvikExpressionNode {

    public enum Op {
        ADD("+"),
        SUB("-"),
        MUL("*"),
        DIV("/");

        private final String spelling;

        Op(String spelling) {
            this.spelling = spelling;
        }
    }

    private final Op op;
    @Child private SolvikExpressionNode left;
    @Child private SolvikExpressionNode right;

    public SolvikNumericBinaryNode(Op op, SolvikExpressionNode left, SolvikExpressionNode right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object a = left.executeGeneric(frame);
        Object b = right.executeGeneric(frame);
        if (a instanceof Byte) {
            return byteResult(((Byte) a).byteValue(), ((Byte) b).byteValue());
        }
        if (a instanceof Short) {
            return shortResult(((Short) a).shortValue(), ((Short) b).shortValue());
        }
        if (a instanceof Integer) {
            return intResult(((Integer) a).intValue(), ((Integer) b).intValue());
        }
        if (a instanceof Long) {
            return longResult(((Long) a).longValue(), ((Long) b).longValue());
        }
        if (a instanceof Float) {
            return floatResult(((Float) a).floatValue(), ((Float) b).floatValue());
        }
        if (a instanceof Double) {
            return doubleResult(((Double) a).doubleValue(), ((Double) b).doubleValue());
        }
        throw new IllegalStateException("invalid operands reached lowered numeric '" + op.spelling + "'");
    }

    private Object byteResult(byte a, byte b) {
        int result;
        switch (op) {
            case ADD -> result = a + b;
            case SUB -> result = a - b;
            case MUL -> result = a * b;
            case DIV -> {
                checkDivisor(b == 0, "division by zero");
                if (a == Byte.MIN_VALUE && b == -1) {
                    throw overflow();
                }
                result = a / b;
            }
            default -> throw new IllegalStateException();
        }
        if (result < Byte.MIN_VALUE || result > Byte.MAX_VALUE) {
            throw overflow();
        }
        return (byte) result;
    }

    private Object shortResult(short a, short b) {
        int result;
        switch (op) {
            case ADD -> result = a + b;
            case SUB -> result = a - b;
            case MUL -> result = a * b;
            case DIV -> {
                checkDivisor(b == 0, "division by zero");
                if (a == Short.MIN_VALUE && b == -1) {
                    throw overflow();
                }
                result = a / b;
            }
            default -> throw new IllegalStateException();
        }
        if (result < Short.MIN_VALUE || result > Short.MAX_VALUE) {
            throw overflow();
        }
        return (short) result;
    }

    private Object intResult(int a, int b) {
        try {
            return switch (op) {
                case ADD -> Math.addExact(a, b);
                case SUB -> Math.subtractExact(a, b);
                case MUL -> Math.multiplyExact(a, b);
                case DIV -> {
                    checkDivisor(b == 0, "division by zero");
                    if (a == Integer.MIN_VALUE && b == -1) {
                        throw overflow();
                    }
                    yield a / b;
                }
            };
        } catch (ArithmeticException e) {
            throw overflow();
        }
    }

    private Object longResult(long a, long b) {
        try {
            return switch (op) {
                case ADD -> Math.addExact(a, b);
                case SUB -> Math.subtractExact(a, b);
                case MUL -> Math.multiplyExact(a, b);
                case DIV -> {
                    checkDivisor(b == 0, "division by zero");
                    if (a == Long.MIN_VALUE && b == -1) {
                        throw overflow();
                    }
                    yield a / b;
                }
            };
        } catch (ArithmeticException e) {
            throw overflow();
        }
    }

    private Object floatResult(float a, float b) {
        return switch (op) {
            case ADD -> a + b;
            case SUB -> a - b;
            case MUL -> a * b;
            case DIV -> a / b;
        };
    }

    private Object doubleResult(double a, double b) {
        return switch (op) {
            case ADD -> a + b;
            case SUB -> a - b;
            case MUL -> a * b;
            case DIV -> a / b;
        };
    }

    private void checkDivisor(boolean zero, String message) {
        if (zero) {
            throw SolvikException.arithmetic(message, this);
        }
    }

    @TruffleBoundary
    private SolvikException overflow() {
        return SolvikException.arithmetic("integer overflow in '" + op.spelling + "'", this);
    }
}
