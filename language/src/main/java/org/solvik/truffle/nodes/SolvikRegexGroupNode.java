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
import org.solvik.truffle.object.SolvikRegexMatch;

/**
 * {@code RegexMatch.group(index: Int): String?} (docs/LANGUAGE_SPEC.md section 14). Group zero is
 * the complete match; a group that did not participate yields {@code null}. An out-of-range index
 * raises a Solvik runtime bounds error. A safe call ({@code receiver?.group(...)}) evaluates the
 * index only for a non-null receiver.
 */
@NodeInfo(shortName = ".group", description = "Read a Solvik RegexMatch capture group")
public final class SolvikRegexGroupNode extends SolvikExpressionNode {

    private final boolean safe;
    @Child private SolvikExpressionNode receiver;
    @Child private SolvikExpressionNode index;

    public SolvikRegexGroupNode(SolvikExpressionNode receiver, SolvikExpressionNode index, boolean safe) {
        this.receiver = receiver;
        this.index = index;
        this.safe = safe;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object target = receiver.executeGeneric(frame);
        if (safe && target == null) {
            return null;
        }
        return ((SolvikRegexMatch) target).group(index.executeInt(frame), this);
    }
}
