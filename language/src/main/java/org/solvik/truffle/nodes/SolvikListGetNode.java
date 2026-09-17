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
 * Reads an element of a built-in {@code List<T>} by index (docs/LANGUAGE_SPEC.md section 11),
 * raising a Solvik runtime bounds error when the index is out of range. The element type was
 * checked statically and is erased at run time.
 */
@NodeInfo(shortName = ".get", description = "Read a Solvik List element")
public final class SolvikListGetNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode receiver;
    @Child private SolvikExpressionNode index;

    public SolvikListGetNode(SolvikExpressionNode receiver, SolvikExpressionNode index) {
        this.receiver = receiver;
        this.index = index;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object target = receiver.executeGeneric(frame);
        int position = index.executeInt(frame);
        return ((SolvikList) target).get(position, this);
    }
}
