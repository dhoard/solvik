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
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikException;

/**
 * A lowered {@code match} expression (docs/LANGUAGE_SPEC.md section 12). The scrutinee is evaluated
 * once and the branches are tried in source order; the first matching clause evaluates its result.
 * Static analysis has already proven the match exhaustive for a known closed variant set (or
 * required a wildcard), so the fall-through path is unreachable for a well-typed program; it raises
 * a runtime type error rather than returning a silent default.
 */
@NodeInfo(shortName = "match", description = "A Solvik exhaustive match expression")
public final class SolvikMatchNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode scrutinee;
    @Children private final SolvikMatchClauseNode[] clauses;

    public SolvikMatchNode(SolvikExpressionNode scrutinee, SolvikMatchClauseNode[] clauses) {
        this.scrutinee = scrutinee;
        this.clauses = clauses;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object value = scrutinee.executeGeneric(frame);
        for (SolvikMatchClauseNode clause : clauses) {
            if (clause.matches(frame, value)) {
                return clause.execute(frame);
            }
        }
        throw noMatch(value);
    }

    @TruffleBoundary
    private SolvikException noMatch(Object value) {
        return SolvikException.typeError("no pattern matched the value in an exhaustive match", this);
    }
}
