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
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObject.GetNode;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Reads a statically declared Solvik object property. The property key was resolved during
 * semantic analysis and the object shape is fixed by its class, so the {@link GetNode} provides a
 * shape-polymorphic inline cache without any runtime name lookup in the guest language.
 */
@NodeInfo(shortName = ".", description = "Read a Solvik object property")
public final class SolvikReadPropertyNode extends SolvikExpressionNode {

    private final TruffleString key;
    @Child private SolvikExpressionNode receiver;
    @Child private GetNode getNode = GetNode.create();

    public SolvikReadPropertyNode(SolvikExpressionNode receiver, TruffleString key) {
        this.receiver = receiver;
        this.key = key;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        DynamicObject target = (DynamicObject) receiver.executeGeneric(frame);
        return getNode.execute(target, key, null);
    }
}
