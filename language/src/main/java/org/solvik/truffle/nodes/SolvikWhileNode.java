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

/** Solvik pre-test {@code while} loop with {@code break} and {@code continue} support. */
@NodeInfo(shortName = "while", description = "A Solvik while loop")
public final class SolvikWhileNode extends SolvikStatementNode {

    @Child private SolvikExpressionNode condition;
    @Child private SolvikStatementNode body;

    public SolvikWhileNode(SolvikExpressionNode condition, SolvikStatementNode body) {
        this.condition = condition;
        this.body = body;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        while (condition.executeBoolean(frame)) {
            try {
                body.executeVoid(frame);
            } catch (SolvikContinueException e) {
                // Continue with the next iteration.
            } catch (SolvikBreakException e) {
                break;
            }
        }
    }
}
