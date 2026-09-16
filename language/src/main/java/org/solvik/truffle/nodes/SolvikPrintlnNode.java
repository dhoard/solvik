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
import org.solvik.truffle.SolvikContext;
import org.solvik.truffle.SolvikUnit;

/** The predeclared Solvik {@code println(value: Any): Unit} function (docs/LANGUAGE_SPEC.md section 6). */
@NodeInfo(shortName = "println", description = "Writes a Solvik value followed by a line separator")
public final class SolvikPrintlnNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode argument;

    public SolvikPrintlnNode(SolvikExpressionNode argument) {
        this.argument = argument;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        SolvikContext.get(this).println(argument.executeGeneric(frame));
        return SolvikUnit.INSTANCE;
    }
}
