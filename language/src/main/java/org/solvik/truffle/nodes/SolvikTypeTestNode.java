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
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikRuntimeTypes;
import org.solvik.type.Type;

/**
 * A Solvik type test {@code value is T} (docs/LANGUAGE_SPEC.md section 18). The written target type
 * was resolved statically and is checked against the runtime representation; the result is always a
 * {@code Boolean}.
 */
@NodeInfo(shortName = "is", description = "A Solvik type test")
public final class SolvikTypeTestNode extends SolvikExpressionNode {

    private final Type target;
    private final SolvikClass targetClass;
    @Child private SolvikExpressionNode operand;

    public SolvikTypeTestNode(Type target, SolvikClass targetClass, SolvikExpressionNode operand) {
        this.target = target;
        this.targetClass = targetClass;
        this.operand = operand;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return SolvikRuntimeTypes.isInstance(operand.executeGeneric(frame), target, targetClass);
    }
}
