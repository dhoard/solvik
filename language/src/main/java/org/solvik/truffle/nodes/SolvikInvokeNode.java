/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikFunction;

/**
 * A call to a declared Solvik function. The target is resolved statically during lowering, so no
 * runtime function lookup is performed; the call itself goes through the target's
 * {@link com.oracle.truffle.api.RootCallTarget}.
 */
@NodeInfo(shortName = "call", description = "A Solvik function call")
public final class SolvikInvokeNode extends SolvikExpressionNode {

    private final SolvikFunction function;
    @Children private final SolvikExpressionNode[] arguments;

    public SolvikInvokeNode(SolvikFunction function, SolvikExpressionNode[] arguments) {
        this.function = function;
        this.arguments = arguments;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object[] values = new Object[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
            values[i] = arguments[i].executeGeneric(frame);
        }
        return function.callTarget().call(values);
    }
}
