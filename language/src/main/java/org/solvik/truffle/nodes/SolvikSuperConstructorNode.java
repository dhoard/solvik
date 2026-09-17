/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * Runs the immediate superclass constructor as the first step of a subclass constructor
 * (docs/LANGUAGE_SPEC.md section 7). The subclass {@code this} is threaded through as the hidden
 * first frame argument so the superclass constructor initializes the same instance.
 */
@NodeInfo(shortName = "super-constructor", description = "Run the immediate superclass constructor")
public final class SolvikSuperConstructorNode extends SolvikStatementNode {

    private final SolvikFunction superConstructor;
    private final int thisSlot;
    @Children private final SolvikExpressionNode[] arguments;

    public SolvikSuperConstructorNode(SolvikFunction superConstructor, int thisSlot, SolvikExpressionNode[] arguments) {
        this.superConstructor = superConstructor;
        this.thisSlot = thisSlot;
        this.arguments = arguments;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        Object[] callArguments = new Object[arguments.length + 1];
        callArguments[0] = frame.getValue(thisSlot);
        for (int i = 0; i < arguments.length; i++) {
            callArguments[i + 1] = arguments[i].executeGeneric(frame);
        }
        superConstructor.callTarget().call(callArguments);
    }
}
