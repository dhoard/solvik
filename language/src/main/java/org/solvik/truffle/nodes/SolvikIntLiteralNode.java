/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

/** A Solvik {@code Int} literal, represented as a primitive {@code int}. */
@NodeInfo(shortName = "int-literal", description = "A 32-bit signed integer literal")
public final class SolvikIntLiteralNode extends SolvikExpressionNode {

    private final int value;

    public SolvikIntLiteralNode(int value) {
        this.value = value;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return value;
    }

    @Override
    public int executeInt(VirtualFrame frame) {
        return value;
    }
}
