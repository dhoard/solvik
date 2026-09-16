/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
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
