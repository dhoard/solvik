/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;

/** Solvik short-circuiting {@code ||}; the right operand is evaluated only when the left is false. */
@NodeInfo(shortName = "||", description = "Solvik short-circuit disjunction")
public final class SolvikLogicalOrNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode left;
    @Child private SolvikExpressionNode right;

    public SolvikLogicalOrNode(SolvikExpressionNode left, SolvikExpressionNode right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return executeBoolean(frame);
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) {
        return left.executeBoolean(frame) || right.executeBoolean(frame);
    }
}
