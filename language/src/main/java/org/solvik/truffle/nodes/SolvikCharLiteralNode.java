/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

/** A Solvik {@code Char} literal, represented as a boxed {@link Character}. */
@NodeInfo(shortName = "char-literal", description = "A character literal")
public final class SolvikCharLiteralNode extends SolvikExpressionNode {

    private final char value;

    public SolvikCharLiteralNode(char value) {
        this.value = value;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return value;
    }
}
