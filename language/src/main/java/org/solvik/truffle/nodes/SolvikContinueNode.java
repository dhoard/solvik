/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

/** Solvik {@code continue}; unwinds to the innermost enclosing loop. */
@NodeInfo(shortName = "continue", description = "A Solvik continue statement")
public final class SolvikContinueNode extends SolvikStatementNode {

    @Override
    public void executeVoid(VirtualFrame frame) {
        throw SolvikContinueException.INSTANCE;
    }
}
