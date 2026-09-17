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

/**
 * Solvik null coalescing {@code left ?? right} (docs/LANGUAGE_SPEC.md section 3). The right operand
 * is evaluated only when the left is {@code null}, so the operator short-circuits exactly like the
 * specification requires. Static analysis guarantees the left operand is nullable.
 */
@NodeInfo(shortName = "??", description = "Solvik null coalescing")
public final class SolvikCoalesceNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode left;
    @Child private SolvikExpressionNode right;

    public SolvikCoalesceNode(SolvikExpressionNode left, SolvikExpressionNode right) {
        this.left = left;
        this.right = right;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object value = left.executeGeneric(frame);
        return value != null ? value : right.executeGeneric(frame);
    }
}
