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
import org.solvik.truffle.object.SolvikRegex;

/**
 * {@code Regex.matches(value: String): Boolean} (docs/LANGUAGE_SPEC.md section 14). The complete
 * input must match; the result is specialized as a primitive {@code boolean}. A safe call
 * ({@code receiver?.matches(...)}) evaluates the argument only for a non-null receiver and
 * otherwise yields {@code null}.
 */
@NodeInfo(shortName = ".matches", description = "Whether a Solvik Regex matches the complete input")
public final class SolvikRegexMatchesNode extends SolvikExpressionNode {

    private final boolean safe;
    @Child private SolvikExpressionNode receiver;
    @Child private SolvikExpressionNode value;

    public SolvikRegexMatchesNode(SolvikExpressionNode receiver, SolvikExpressionNode value, boolean safe) {
        this.receiver = receiver;
        this.value = value;
        this.safe = safe;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object target = receiver.executeGeneric(frame);
        if (safe && target == null) {
            return null;
        }
        return matches(target, frame);
    }

    @Override
    public boolean executeBoolean(VirtualFrame frame) {
        Object target = receiver.executeGeneric(frame);
        if (safe && target == null) {
            throw new IllegalStateException("a safe Regex call yielded null where a Boolean was expected");
        }
        return matches(target, frame);
    }

    private boolean matches(Object target, VirtualFrame frame) {
        return ((SolvikRegex) target).matches((String) value.executeGeneric(frame));
    }
}
