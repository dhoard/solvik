/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

/** Solvik {@code break}; unwinds to the innermost enclosing loop. */
@NodeInfo(shortName = "break", description = "A Solvik break statement")
public final class SolvikBreakNode extends SolvikStatementNode {

    @Override
    public void executeVoid(VirtualFrame frame) {
        throw SolvikBreakException.INSTANCE;
    }
}
