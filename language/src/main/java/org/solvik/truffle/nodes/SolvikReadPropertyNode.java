/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
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
 * shape-polymorphic inline cache without any runtime name lookup in the guest language. A safe read
 * ({@code receiver?.property}) evaluates to {@code null} without touching the shape when the
 * receiver is {@code null}.
 */
@NodeInfo(shortName = ".", description = "Read a Solvik object property")
public final class SolvikReadPropertyNode extends SolvikExpressionNode {

    private final TruffleString key;
    private final boolean safe;
    @Child private SolvikExpressionNode receiver;
    @Child private GetNode getNode = GetNode.create();

    public SolvikReadPropertyNode(SolvikExpressionNode receiver, TruffleString key) {
        this(receiver, key, false);
    }

    public SolvikReadPropertyNode(SolvikExpressionNode receiver, TruffleString key, boolean safe) {
        this.receiver = receiver;
        this.key = key;
        this.safe = safe;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object target = receiver.executeGeneric(frame);
        if (safe && target == null) {
            return null;
        }
        return getNode.execute((DynamicObject) target, key, null);
    }
}
