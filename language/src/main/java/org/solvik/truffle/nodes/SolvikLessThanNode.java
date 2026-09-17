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

/** Solvik comparison on {@code Int}, producing a primitive {@code boolean}. */
@NodeChild("leftNode")
@NodeChild("rightNode")
@NodeInfo(shortName = "<", description = "Solvik integer ordering comparison")
public abstract class SolvikLessThanNode extends SolvikExpressionNode {

    @Specialization
    protected boolean doInt(int left, int right) {
        return left < right;
    }

    @Fallback
    protected boolean doOther(Object left, Object right) {
        throw new IllegalStateException("invalid operands reached lowered '<'");
    }
}
