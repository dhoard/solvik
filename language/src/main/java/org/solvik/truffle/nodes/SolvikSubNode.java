/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikException;

/** Solvik {@code -} on {@code Integer}, checked for 32-bit overflow. */
@NodeChild("leftNode")
@NodeChild("rightNode")
@NodeInfo(shortName = "-", description = "Solvik integer subtraction")
public abstract class SolvikSubNode extends SolvikExpressionNode {

    @Specialization
    protected int doInt(int left, int right) {
        try {
            return Math.subtractExact(left, right);
        } catch (ArithmeticException e) {
            throw SolvikException.arithmetic("integer overflow in '-'", this);
        }
    }

    @Fallback
    protected int doOther(Object left, Object right) {
        throw new IllegalStateException("invalid operands reached lowered '-'");
    }
}
