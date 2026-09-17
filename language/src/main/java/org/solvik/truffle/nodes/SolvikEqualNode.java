/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikValues;

/**
 * Solvik {@code ==}. Built-in scalars compare by value; ordinary class instances compare by
 * identity (docs/LANGUAGE_SPEC.md section 3). Static analysis guarantees the operands are
 * assignment-compatible in at least one direction.
 */
@NodeChild("leftNode")
@NodeChild("rightNode")
@NodeInfo(shortName = "==", description = "Solvik equality comparison")
public abstract class SolvikEqualNode extends SolvikExpressionNode {

    @Specialization
    protected boolean doInt(int left, int right) {
        return left == right;
    }

    @Specialization
    protected boolean doBoolean(boolean left, boolean right) {
        return left == right;
    }

    @Specialization
    protected boolean doString(String left, String right) {
        return left.equals(right);
    }

    @Specialization(replaces = {"doInt", "doBoolean", "doString"})
    protected boolean doObject(Object left, Object right) {
        // One shared definition of Solvik equality keeps expression {@code ==} and collection
        // membership consistent (docs/LANGUAGE_SPEC.md section 3).
        return SolvikValues.equal(left, right);
    }
}
