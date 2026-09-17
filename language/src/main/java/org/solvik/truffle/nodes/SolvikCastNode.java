/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikException;
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikRuntimeTypes;
import org.solvik.type.Type;

/**
 * A checked cast {@code value as T} (docs/LANGUAGE_SPEC.md section 18). The cast evaluates to the
 * unchanged value when the runtime representation matches {@code T} and otherwise raises a Solvik
 * runtime type error; there is no safe-cast syntax.
 */
@NodeInfo(shortName = "as", description = "A Solvik checked cast")
public final class SolvikCastNode extends SolvikExpressionNode {

    private final Type target;
    private final SolvikClass targetClass;
    @Child private SolvikExpressionNode operand;

    public SolvikCastNode(Type target, SolvikClass targetClass, SolvikExpressionNode operand) {
        this.target = target;
        this.targetClass = targetClass;
        this.operand = operand;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object value = operand.executeGeneric(frame);
        if (!SolvikRuntimeTypes.isInstance(value, target, targetClass)) {
            throw castFailure(value);
        }
        return value;
    }

    @TruffleBoundary
    private SolvikException castFailure(Object value) {
        String actual = value == null ? "null" : value.getClass().getSimpleName();
        return SolvikException.typeError("cannot cast " + actual + " to " + target.name(), this);
    }
}
