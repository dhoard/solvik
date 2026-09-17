/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.solvik.source;

/**
 * A half-open character span {@code [startOffset, endOffset)} using zero-based character offsets
 * into the {@link SourceFile} identified by {@code sourceId}.
 *
 * <p>The span is the authoritative source location for AST nodes and diagnostics. Line and column
 * positions are derived from a {@link SourceFile} for display only and are never stored. Because
 * separately parsed include files reuse the same offset ranges, the {@code sourceId} is part of the
 * span and two spans only compare, contain, or overlap when they belong to the same source.
 */
public record SourceSpan(int sourceId, int startOffset, int endOffset) implements Comparable<SourceSpan> {

    public SourceSpan {
        if (sourceId < 0) {
            throw new IllegalArgumentException("sourceId must be >= 0 but was " + sourceId);
        }
        if (startOffset < 0) {
            throw new IllegalArgumentException("startOffset must be >= 0 but was " + startOffset);
        }
        if (endOffset < startOffset) {
            throw new IllegalArgumentException(
                    "endOffset " + endOffset + " must be >= startOffset " + startOffset);
        }
    }

    /** A span in the root compilation source ({@code sourceId} 0). */
    public static SourceSpan of(int startOffset, int endOffset) {
        return new SourceSpan(0, startOffset, endOffset);
    }

    /** A span in an arbitrary physical source. */
    public static SourceSpan of(int sourceId, int startOffset, int endOffset) {
        return new SourceSpan(sourceId, startOffset, endOffset);
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
        return other.sourceId == sourceId && other.startOffset >= startOffset && other.endOffset <= endOffset;
    }

    public boolean overlaps(SourceSpan other) {
        return other.sourceId == sourceId && startOffset < other.endOffset && other.startOffset < endOffset;
    }

    @Override
    public int compareTo(SourceSpan other) {
        int c = Integer.compare(sourceId, other.sourceId);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(startOffset, other.startOffset);
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
