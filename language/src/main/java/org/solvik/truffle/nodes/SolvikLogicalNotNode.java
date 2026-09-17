/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;

/** Solvik unary {@code !} on {@code Boolean}. */
@NodeChild("valueNode")
@NodeInfo(shortName = "!", description = "Solvik logical negation")
public abstract class SolvikLogicalNotNode extends SolvikExpressionNode {

    @Specialization
    protected boolean doBoolean(boolean value) {
        return !value;
    }

    @Fallback
    protected boolean doOther(Object value) {
        throw new IllegalStateException("invalid operand reached lowered '!'");
    }
}
