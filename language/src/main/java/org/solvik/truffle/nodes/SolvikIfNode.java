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

/** Solvik {@code if (condition) ... else ...}; the else branch is absent for a bare {@code if}. */
@NodeInfo(shortName = "if", description = "A Solvik if statement")
public final class SolvikIfNode extends SolvikStatementNode {

    @Child private SolvikExpressionNode condition;
    @Child private SolvikStatementNode thenBlock;
    @Child private SolvikStatementNode elseBranch;

    public SolvikIfNode(SolvikExpressionNode condition, SolvikStatementNode thenBlock, SolvikStatementNode elseBranch) {
        this.condition = condition;
        this.thenBlock = thenBlock;
        this.elseBranch = elseBranch;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        if (condition.executeBoolean(frame)) {
            thenBlock.executeVoid(frame);
        } else if (elseBranch != null) {
            elseBranch.executeVoid(frame);
        }
    }
}
