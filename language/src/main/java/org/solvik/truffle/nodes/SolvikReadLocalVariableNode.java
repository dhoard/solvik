/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * Reads a local variable or parameter from the Solvik frame. The frame slot kind is fixed by the
 * binding's static type during lowering, so an {@code Integer} or {@code Boolean} binding is read as a
 * primitive without boxing.
 */
@NodeField(name = "slot", type = int.class)
@NodeInfo(shortName = "read-local", description = "Read a Solvik local variable")
public abstract class SolvikReadLocalVariableNode extends SolvikExpressionNode {

    protected abstract int getSlot();

    @Specialization(guards = "frame.isInt(getSlot())")
    protected int readInt(VirtualFrame frame) {
        return frame.getInt(getSlot());
    }

    @Specialization(guards = "frame.isBoolean(getSlot())")
    protected boolean readBoolean(VirtualFrame frame) {
        return frame.getBoolean(getSlot());
    }

    @Specialization(guards = "frame.isLong(getSlot())")
    protected long readLong(VirtualFrame frame) {
        return frame.getLong(getSlot());
    }

    @Specialization(guards = "frame.isFloat(getSlot())")
    protected float readFloat(VirtualFrame frame) {
        return frame.getFloat(getSlot());
    }

    @Specialization(guards = "frame.isDouble(getSlot())")
    protected double readDouble(VirtualFrame frame) {
        return frame.getDouble(getSlot());
    }

    @Specialization(replaces = {"readInt", "readBoolean", "readLong", "readFloat", "readDouble"})
    protected Object readObject(VirtualFrame frame) {
        return frame.getValue(getSlot());
    }
}
