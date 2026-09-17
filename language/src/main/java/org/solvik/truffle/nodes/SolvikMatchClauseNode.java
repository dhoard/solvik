/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * One lowered branch of a {@code match} (docs/LANGUAGE_SPEC.md section 12): a pattern matcher and
 * the result expression evaluated when the pattern matches. The clause is not itself an expression;
 * the enclosing {@link SolvikMatchNode} owns the branch list so patterns and results stay in source
 * order.
 */
@NodeInfo(description = "One Solvik match branch")
public final class SolvikMatchClauseNode extends Node {

    @Child private SolvikPatternNode pattern;
    @Child private SolvikExpressionNode result;

    public SolvikMatchClauseNode(SolvikPatternNode pattern, SolvikExpressionNode result) {
        this.pattern = pattern;
        this.result = result;
    }

    /** Tests the pattern against {@code value}, binding names into {@code frame} when it matches. */
    public boolean matches(VirtualFrame frame, Object value) {
        return pattern.matches(frame, value);
    }

    /** Evaluates the branch result after a successful pattern match. */
    public Object execute(VirtualFrame frame) {
        return result.executeGeneric(frame);
    }
}
