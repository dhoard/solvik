/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.source;

/**
 * A 1-based line/column position derived from a zero-based character offset for display only.
 * Positions are never the authoritative source location; {@link SourceSpan} is.
 */
public record LineColumn(int line, int column) implements Comparable<LineColumn> {

    public LineColumn {
        if (line < 1) {
            throw new IllegalArgumentException("line must be >= 1 but was " + line);
        }
        if (column < 1) {
            throw new IllegalArgumentException("column must be >= 1 but was " + column);
        }
    }

    @Override
    public int compareTo(LineColumn other) {
        int c = Integer.compare(line, other.line);
        return c != 0 ? c : Integer.compare(column, other.column);
    }

    @Override
    public String toString() {
        return line + ":" + column;
    }
}
