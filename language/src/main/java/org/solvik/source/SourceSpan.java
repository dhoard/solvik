/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.source;

/**
 * A half-open character span {@code [startOffset, endOffset)} using zero-based character offsets
 * into a {@link SourceFile}.
 *
 * <p>The span is the authoritative source location for AST nodes and diagnostics. Line and column
 * positions are derived from a {@link SourceFile} for display only and are never stored.
 */
public record SourceSpan(int startOffset, int endOffset) implements Comparable<SourceSpan> {

    public SourceSpan {
        if (startOffset < 0) {
            throw new IllegalArgumentException("startOffset must be >= 0 but was " + startOffset);
        }
        if (endOffset < startOffset) {
            throw new IllegalArgumentException(
                    "endOffset " + endOffset + " must be >= startOffset " + startOffset);
        }
    }

    public static SourceSpan of(int startOffset, int endOffset) {
        return new SourceSpan(startOffset, endOffset);
    }

    /** Length in characters; zero for empty (point-like) spans. */
    public int length() {
        return endOffset - startOffset;
    }

    public boolean isEmpty() {
        return startOffset == endOffset;
    }

    public boolean contains(int offset) {
        return offset >= startOffset && offset < endOffset;
    }

    public boolean contains(SourceSpan other) {
        return other.startOffset >= startOffset && other.endOffset <= endOffset;
    }

    public boolean overlaps(SourceSpan other) {
        return startOffset < other.endOffset && other.startOffset < endOffset;
    }

    @Override
    public int compareTo(SourceSpan other) {
        int c = Integer.compare(startOffset, other.startOffset);
        if (c != 0) {
            return c;
        }
        return Integer.compare(endOffset, other.endOffset);
    }

    @Override
    public String toString() {
        return "[" + startOffset + ".." + endOffset + ")";
    }
}
