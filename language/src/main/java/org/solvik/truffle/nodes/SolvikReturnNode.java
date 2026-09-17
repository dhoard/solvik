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
import org.solvik.truffle.SolvikUnit;

/** Solvik {@code return;} or {@code return expression;}. Unwinds the function body. */
@NodeInfo(shortName = "return", description = "A Solvik return statement")
public final class SolvikReturnNode extends SolvikStatementNode {

    @Child private SolvikExpressionNode value;

    /** @param value the returned expression, or {@code null} for a bare {@code return;} */
    public SolvikReturnNode(SolvikExpressionNode value) {
        this.value = value;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        throw new SolvikReturnException(value == null ? SolvikUnit.INSTANCE : value.executeGeneric(frame));
    }
}
