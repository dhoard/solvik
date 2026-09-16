/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;

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
        if (left instanceof Integer a && right instanceof Integer b) {
            return a.intValue() == b.intValue();
        }
        if (left instanceof Boolean a && right instanceof Boolean b) {
            return a.booleanValue() == b.booleanValue();
        }
        if (left instanceof String a && right instanceof String b) {
            return a.equals(b);
        }
        if (left instanceof Byte a && right instanceof Byte b) {
            return a.byteValue() == b.byteValue();
        }
        if (left instanceof Short a && right instanceof Short b) {
            return a.shortValue() == b.shortValue();
        }
        if (left instanceof Long a && right instanceof Long b) {
            return a.longValue() == b.longValue();
        }
        if (left instanceof Float a && right instanceof Float b) {
            return a.floatValue() == b.floatValue();
        }
        if (left instanceof Double a && right instanceof Double b) {
            return a.doubleValue() == b.doubleValue();
        }
        if (left instanceof Character a && right instanceof Character b) {
            return a.charValue() == b.charValue();
        }
        // Ordinary class instances compare by identity in Solvik.
        return left == right;
    }
}
