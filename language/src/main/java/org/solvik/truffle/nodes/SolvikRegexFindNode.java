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
 * {@code Regex.find(value: String): RegexMatch?} (docs/LANGUAGE_SPEC.md section 14). Returns the
 * first non-overlapping match, or {@code null} when there is none. A safe call
 * ({@code receiver?.find(...)}) evaluates the argument only for a non-null receiver.
 */
@NodeInfo(shortName = ".find", description = "The first Solvik Regex match")
public final class SolvikRegexFindNode extends SolvikExpressionNode {

    private final boolean safe;
    @Child private SolvikExpressionNode receiver;
    @Child private SolvikExpressionNode value;

    public SolvikRegexFindNode(SolvikExpressionNode receiver, SolvikExpressionNode value, boolean safe) {
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
        return ((SolvikRegex) target).find((String) value.executeGeneric(frame));
    }
}
