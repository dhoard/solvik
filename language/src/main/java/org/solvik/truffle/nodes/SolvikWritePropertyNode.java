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
import com.oracle.truffle.api.object.DynamicObject.PutNode;
import com.oracle.truffle.api.strings.TruffleString;

/**
 * Writes a statically declared Solvik object property. The property key and mutability were
 * resolved during semantic analysis, so an immutable property cannot reach this node more than
 * once and undeclared properties are impossible.
 */
@NodeInfo(shortName = ".=", description = "Write a Solvik object property")
public final class SolvikWritePropertyNode extends SolvikExpressionNode {

    private final TruffleString key;
    @Child private SolvikExpressionNode receiver;
    @Child private SolvikExpressionNode value;
    @Child private PutNode putNode = PutNode.create();

    public SolvikWritePropertyNode(SolvikExpressionNode receiver, SolvikExpressionNode value, TruffleString key) {
        this.receiver = receiver;
        this.value = value;
        this.key = key;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        DynamicObject target = (DynamicObject) receiver.executeGeneric(frame);
        Object newValue = value.executeGeneric(frame);
        putNode.execute(target, key, newValue);
        return newValue;
    }
}
