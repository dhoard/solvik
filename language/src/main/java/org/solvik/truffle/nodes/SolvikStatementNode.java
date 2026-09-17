/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Base class for Solvik statements. Statements do not produce a value; a value-producing expression
 * used as a statement executes through {@link SolvikExpressionNode#executeVoid(VirtualFrame)}.
 *
 * <p>Every lowered statement retains its own physical Truffle {@link Source} plus a character index
 * and length. A cross-file implicit {@code main} can contain statements from several files, so a
 * statement can no longer derive its source from its enclosing root; {@link #getSourceSection()}
 * uses the stored source directly.
 */
@NodeInfo(description = "The abstract base node for all Solvik statements")
public abstract class SolvikStatementNode extends Node {

    private static final int NO_SOURCE = -1;

    private Source source;
    private int sourceCharIndex = NO_SOURCE;
    private int sourceLength;

    /** Records this node's authoritative physical source and span during lowering. */
    public final void setSourceSection(Source source, int charIndex, int length) {
        if (sourceCharIndex != NO_SOURCE) {
            throw new IllegalStateException("source section already set");
        }
        if (source == null) {
            throw new NullPointerException("source");
        }
        if (charIndex < 0 || length < 0) {
            throw new IllegalArgumentException("negative source offset or length");
        }
        this.source = source;
        this.sourceCharIndex = charIndex;
        this.sourceLength = length;
    }

    @Override
    public SourceSection getSourceSection() {
        if (sourceCharIndex == NO_SOURCE || source == null) {
            return null;
        }
        int start = Math.min(sourceCharIndex, source.getLength());
        int length = Math.min(sourceLength, source.getLength() - start);
        return source.createSection(start, length);
    }

    /** Executes this statement for its effect. */
    public abstract void executeVoid(VirtualFrame frame);
}
