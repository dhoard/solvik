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
 * Reads one of the immutable {@code RegexMatch} properties (docs/LANGUAGE_SPEC.md section 14):
 * {@code value: String}, {@code start: Int}, {@code end: Int}, or {@code groupCount: Int}. The
 * built-in match has no Truffle shape storage, so lowering emits this dedicated read. A safe read
 * ({@code receiver?.value}) yields {@code null} for a null receiver.
 */
@NodeInfo(shortName = ".match", description = "Read a Solvik RegexMatch property")
public final class SolvikRegexMatchReadNode extends SolvikExpressionNode {

    /** The property this node reads. */
    public enum Field {
        VALUE,
        START,
        END,
        GROUP_COUNT
    }

    private final Field field;
    private final boolean safe;
    @Child private SolvikExpressionNode receiver;

    public SolvikRegexMatchReadNode(Field field, SolvikExpressionNode receiver, boolean safe) {
        this.field = field;
        this.receiver = receiver;
        this.safe = safe;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object target = receiver.executeGeneric(frame);
        if (safe && target == null) {
            return null;
        }
        SolvikRegexMatch match = (SolvikRegexMatch) target;
        return switch (field) {
            case VALUE -> match.value();
            case START -> match.start();
            case END -> match.end();
            case GROUP_COUNT -> match.groupCount();
        };
    }

    @Override
    public int executeInt(VirtualFrame frame) {
        Object target = receiver.executeGeneric(frame);
        if (safe && target == null) {
            throw new IllegalStateException("a safe RegexMatch read yielded null where an Int was expected");
        }
        SolvikRegexMatch match = (SolvikRegexMatch) target;
        return switch (field) {
            case START -> match.start();
            case END -> match.end();
            case GROUP_COUNT -> match.groupCount();
            case VALUE -> throw new IllegalStateException("RegexMatch.value is a String, not an Int");
        };
    }
}
