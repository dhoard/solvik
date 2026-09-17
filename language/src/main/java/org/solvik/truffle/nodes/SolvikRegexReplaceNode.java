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
 * {@code Regex.replace(value: String, replacement: String): String}
 * (docs/LANGUAGE_SPEC.md section 14). Replaces every non-overlapping match and treats the
 * replacement as literal text; capture substitution is deferred. A safe call
 * ({@code receiver?.replace(...)}) evaluates the arguments only for a non-null receiver.
 */
@NodeInfo(shortName = ".replace", description = "Replace all Solvik Regex matches")
public final class SolvikRegexReplaceNode extends SolvikExpressionNode {

    private final boolean safe;
    @Child private SolvikExpressionNode receiver;
    @Child private SolvikExpressionNode value;
    @Child private SolvikExpressionNode replacement;

    public SolvikRegexReplaceNode(SolvikExpressionNode receiver, SolvikExpressionNode value, SolvikExpressionNode replacement, boolean safe) {
        this.receiver = receiver;
        this.value = value;
        this.replacement = replacement;
        this.safe = safe;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object target = receiver.executeGeneric(frame);
        if (safe && target == null) {
            return null;
        }
        return ((SolvikRegex) target).replace((String) value.executeGeneric(frame), (String) replacement.executeGeneric(frame));
    }
}
