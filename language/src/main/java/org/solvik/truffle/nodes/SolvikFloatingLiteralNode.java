/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * A Solvik floating-point literal. A literal without the {@code f}/{@code F} suffix produces a
 * primitive {@code double}; the suffix narrows the same decimal text to a {@code float}.
 */
@NodeInfo(shortName = "float-literal", description = "A decimal floating-point literal")
public final class SolvikFloatingLiteralNode extends SolvikExpressionNode {

    private final boolean isFloat;
    private final double value;

    public SolvikFloatingLiteralNode(boolean isFloat, double value) {
        this.isFloat = isFloat;
        this.value = value;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return isFloat ? (float) value : value;
    }

    @Override
    public float executeFloat(VirtualFrame frame) {
        return (float) value;
    }

    @Override
    public double executeDouble(VirtualFrame frame) {
        return value;
    }
}
