/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.object.SolvikList;

/**
 * Reads the {@code size} of a built-in {@code List<T>} (docs/LANGUAGE_SPEC.md section 11). The
 * built-in list has no declared property storage, so lowering emits this dedicated read instead of
 * a Truffle shape access.
 */
@NodeInfo(shortName = ".size", description = "Read the size of a Solvik List")
public final class SolvikListSizeNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode receiver;

    public SolvikListSizeNode(SolvikExpressionNode receiver) {
        this.receiver = receiver;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return ((SolvikList) receiver.executeGeneric(frame)).size();
    }

    @Override
    public int executeInt(VirtualFrame frame) {
        return ((SolvikList) receiver.executeGeneric(frame)).size();
    }
}
