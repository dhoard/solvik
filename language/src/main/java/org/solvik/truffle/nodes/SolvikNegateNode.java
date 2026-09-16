/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/** Solvik unary {@code -} on {@code Int}, checked for 32-bit overflow. */
@NodeChild("valueNode")
@NodeInfo(shortName = "-", description = "Solvik integer negation")
public abstract class SolvikNegateNode extends SolvikExpressionNode {

    @Specialization
    protected int doInt(int value) {
        try {
            return Math.negateExact(value);
        } catch (ArithmeticException e) {
            throw SolvikException.arithmetic("integer overflow in unary '-'", this);
        }
    }

    @Fallback
    protected int doOther(Object value) {
        throw new IllegalStateException("invalid operand reached lowered unary '-'");
    }
}
