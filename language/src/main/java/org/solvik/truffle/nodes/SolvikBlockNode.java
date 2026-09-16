/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;

/** A sequence of Solvik statements executed in source order. */
@NodeInfo(shortName = "block", description = "A Solvik block")
public final class SolvikBlockNode extends SolvikStatementNode {

    @Children private final SolvikStatementNode[] statements;

    public SolvikBlockNode(SolvikStatementNode[] statements) {
        this.statements = statements;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        for (SolvikStatementNode statement : statements) {
            statement.executeVoid(frame);
        }
    }
}
