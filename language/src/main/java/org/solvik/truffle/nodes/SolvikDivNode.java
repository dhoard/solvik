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

/**
 * Solvik {@code /} on {@code Int}: truncating integer division. Division by zero and the single
 * overflowing case ({@code Int.MIN_VALUE / -1}) raise a Solvik runtime arithmetic error
 * (docs/LANGUAGE_SPEC.md section 3).
 */
@NodeChild("leftNode")
@NodeChild("rightNode")
@NodeInfo(shortName = "/", description = "Solvik integer division")
public abstract class SolvikDivNode extends SolvikExpressionNode {

    @Specialization
    protected int doInt(int left, int right) {
        if (right == 0) {
            throw SolvikException.arithmetic("division by zero", this);
        }
        if (left == Integer.MIN_VALUE && right == -1) {
            throw SolvikException.arithmetic("integer overflow in '/'", this);
        }
        return left / right;
    }

    @Fallback
    protected int doOther(Object left, Object right) {
        throw new IllegalStateException("invalid operands reached lowered '/'");
    }
}
