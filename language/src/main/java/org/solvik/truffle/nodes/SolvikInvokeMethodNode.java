/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikFunction;
import org.solvik.truffle.object.SolvikObject;

/**
 * Invokes an instance method. With single inheritance, an ordinary call must dispatch on the
 * receiver's runtime class, so the node looks the method up in the receiver's {@code SolvikClass}
 * method table by name. A {@code super.member(...)} call passes a fixed target and bypasses that
 * dispatch to run the immediate superclass implementation. The receiver is always passed as the
 * hidden first frame argument so the method body reads it through the {@code this} slot.
 */
@NodeInfo(shortName = "call", description = "Invoke a Solvik instance method")
public final class SolvikInvokeMethodNode extends SolvikExpressionNode {

    /** Direct target for {@code super.member(...)}; {@code null} selects virtual dispatch. */
    private final SolvikFunction directTarget;
    private final String methodName;
    @Child private SolvikExpressionNode receiver;
    @Children private final SolvikExpressionNode[] arguments;

    /** Creates a virtually dispatched call by method name. */
    public SolvikInvokeMethodNode(String methodName, SolvikExpressionNode receiver, SolvikExpressionNode[] arguments) {
        this.directTarget = null;
        this.methodName = methodName;
        this.receiver = receiver;
        this.arguments = arguments;
    }

    /** Creates a direct call to a fixed implementation, as required by {@code super.member(...)}. */
    public SolvikInvokeMethodNode(SolvikFunction target, SolvikExpressionNode receiver, SolvikExpressionNode[] arguments) {
        this.directTarget = target;
        this.methodName = target.name();
        this.receiver = receiver;
        this.arguments = arguments;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object instance = receiver.executeGeneric(frame);
        Object[] callArguments = new Object[arguments.length + 1];
        callArguments[0] = instance;
        for (int i = 0; i < arguments.length; i++) {
            callArguments[i + 1] = arguments[i].executeGeneric(frame);
        }
        SolvikFunction target = directTarget;
        if (target == null) {
            if (!(instance instanceof SolvikObject object)) {
                throw new IllegalStateException("method call receiver is not a Solvik object");
            }
            target = object.solvikClass().method(methodName);
            if (target == null) {
                throw new IllegalStateException("no method '" + methodName + "' on class " + object.solvikClass().name());
            }
        }
        return target.callTarget().call(callArguments);
    }
}
