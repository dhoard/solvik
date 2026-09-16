/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Base class for Solvik statements. Statements do not produce a value; a value-producing expression
 * used as a statement executes through {@link SolvikExpressionNode#executeVoid(VirtualFrame)}.
 *
 * <p>Source locations use the same lazy scheme as the parent language: lowering records the
 * character index and length only, and {@link #getSourceSection()} creates the Truffle section from
 * the root node's source when diagnostics or the debugger ask for it.
 */
@NodeInfo(description = "The abstract base node for all Solvik statements")
public abstract class SolvikStatementNode extends Node {

    private static final int NO_SOURCE = -1;

    private int sourceCharIndex = NO_SOURCE;
    private int sourceLength;

    /** Records this node's authoritative source span from the syntax AST during lowering. */
    public final void setSourceSection(int charIndex, int length) {
        if (sourceCharIndex != NO_SOURCE) {
            throw new IllegalStateException("source section already set");
        }
        if (charIndex < 0 || length < 0) {
            throw new IllegalArgumentException("negative source offset or length");
        }
        this.sourceCharIndex = charIndex;
        this.sourceLength = length;
    }

    @Override
    public SourceSection getSourceSection() {
        if (sourceCharIndex == NO_SOURCE) {
            return null;
        }
        RootNode rootNode = getRootNode();
        if (rootNode == null) {
            return null;
        }
        SourceSection rootSection = rootNode.getSourceSection();
        if (rootSection == null) {
            return null;
        }
        Source source = rootSection.getSource();
        if (source == null) {
            return null;
        }
        int start = Math.min(sourceCharIndex, source.getLength());
        int length = Math.min(sourceLength, source.getLength() - start);
        return source.createSection(start, length);
    }

    /** Executes this statement for its effect. */
    public abstract void executeVoid(VirtualFrame frame);
}
