/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikException;

/**
 * Solvik {@code +}: checked 32-bit integer addition (docs/LANGUAGE_SPEC.md section 3) or
 * {@code String} concatenation. Primitive {@code int} addition is specialized; the fallback is
 * unreachable because static analysis rejects every other operand combination before lowering.
 */
@NodeChild("leftNode")
@NodeChild("rightNode")
@NodeInfo(shortName = "+", description = "Solvik addition or string concatenation")
public abstract class SolvikAddNode extends SolvikExpressionNode {

    @Specialization
    protected int doInt(int left, int right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException e) {
            throw SolvikException.arithmetic("integer overflow in '+'", this);
        }
    }

    @Specialization
    @TruffleBoundary
    protected String doString(String left, String right) {
        return left + right;
    }

    @Fallback
    protected Object doOther(Object left, Object right) {
        throw new IllegalStateException("invalid operands reached lowered '+'");
    }
}
