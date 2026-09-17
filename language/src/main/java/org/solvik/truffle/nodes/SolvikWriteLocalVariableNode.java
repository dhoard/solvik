/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * Writes a local or parameter slot of the Solvik frame. The slot kind was fixed by the statically
 * analysed binding type during lowering, so an {@code Int} or {@code Boolean} binding is stored as a
 * primitive without boxing.
 */
@NodeChild("valueNode")
@NodeField(name = "slot", type = int.class)
@NodeInfo(shortName = "write-local", description = "Write a Solvik local variable")
public abstract class SolvikWriteLocalVariableNode extends SolvikExpressionNode {

    protected abstract int getSlot();

    @Specialization(guards = "frame.isInt(getSlot())")
    protected int writeInt(VirtualFrame frame, int value) {
        frame.setInt(getSlot(), value);
        return value;
    }

    @Specialization(guards = "frame.isBoolean(getSlot())")
    protected boolean writeBoolean(VirtualFrame frame, boolean value) {
        frame.setBoolean(getSlot(), value);
        return value;
    }

    @Specialization(guards = "frame.isLong(getSlot())")
    protected long writeLong(VirtualFrame frame, long value) {
        frame.setLong(getSlot(), value);
        return value;
    }

    @Specialization(guards = "frame.isFloat(getSlot())")
    protected float writeFloat(VirtualFrame frame, float value) {
        frame.setFloat(getSlot(), value);
        return value;
    }

    @Specialization(guards = "frame.isDouble(getSlot())")
    protected double writeDouble(VirtualFrame frame, double value) {
        frame.setDouble(getSlot(), value);
        return value;
    }

    @Specialization(replaces = {"writeInt", "writeBoolean", "writeLong", "writeFloat", "writeDouble"})
    protected Object writeObject(VirtualFrame frame, Object value) {
        frame.setObject(getSlot(), value);
        return value;
    }
}
