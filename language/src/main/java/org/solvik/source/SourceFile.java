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

import java.util.Objects;

/**
 * An immutable named chunk of Solvik source text with line-index support used to derive display
 * positions from authoritative {@link SourceSpan} offsets.
 *
 * <p>Physical line breaks are {@code \n}, {@code \r}, and {@code \r\n} (counted as one break). This
 * mirrors the physical-newline notion that semicolon insertion (Phase 2) will rely on.
 */
public final class SourceFile {

    private final int id;
    private final String name;
    private final String text;
    /** Start offset of each physical line; index i corresponds to line number i + 1. */
    private final int[] lineStarts;

    /** Creates a source whose compilation-local identity is the root source id {@code 0}. */
    public SourceFile(String name, String text) {
        this(0, name, text);
    }

    /**
     * Creates a source with an explicit compilation-local identity. Included files receive
     * monotonically increasing ids so that spans from separately parsed files never collide.
     */
    public SourceFile(int id, String name, String text) {
        if (id < 0) {
            throw new IllegalArgumentException("id must be >= 0 but was " + id);
        }
        this.id = id;
        this.name = Objects.requireNonNull(name);
        this.text = Objects.requireNonNull(text);
        this.lineStarts = computeLineStarts(text);
    }

    /** The compilation-local source identity used by every {@link SourceSpan} in this file. */
    public int id() {
        return id;
    }

    private static int[] computeLineStarts(String text) {
        int[] tmp = new int[16];
        int count = 0;
        tmp[count++] = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            int nextBreakAfter;
            if (c == '\r') {
                nextBreakAfter = (i + 1 < text.length() && text.charAt(i + 1) == '\n') ? i + 2 : i + 1;
            } else if (c == '\n') {
                nextBreakAfter = i + 1;
            } else {
                i++;
                continue;
            }
            if (count == tmp.length) {
                int[] grown = new int[tmp.length * 2];
                System.arraycopy(tmp, 0, grown, 0, count);
                tmp = grown;
            }
            tmp[count++] = nextBreakAfter;
            i = nextBreakAfter;
        }
        int[] result = new int[count];
        System.arraycopy(tmp, 0, result, 0, count);
        return result;
    }

    public String name() {
        return name;
    }

    public String text() {
        return text;
    }

    public int textLength() {
        return text.length();
    }

    /** Number of physical lines; always >= 1. */
    public int lineCount() {
        return lineStarts.length;
    }

    /** Derives the 1-based display position of a zero-based character offset. */
    public LineColumn lineColumnAt(int offset) {
        int capped = Math.max(0, Math.min(offset, text.length()));
        int low = 0;
        int high = lineStarts.length - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (lineStarts[mid] <= capped) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return new LineColumn(low + 1, capped - lineStarts[low] + 1);
    }

    /**
     * Converts a 1-based line and column (as produced by {@link #lineColumnAt}) back to a
     * zero-based character offset. The result is capped at the first character of the next line so
     * that every derived position round-trips, including positions pointing at line-break
     * characters.
     */
    public int offsetAt(int line, int column) {
        if (line < 1 || line > lineStarts.length) {
            throw new IllegalArgumentException("line " + line + " out of range 1.." + lineStarts.length);
        }
        if (column < 1) {
            throw new IllegalArgumentException("column must be >= 1 but was " + column);
        }
        int lineStart = lineStarts[line - 1];
        int limit = line < lineStarts.length ? lineStarts[line] : text.length();
        return Math.max(lineStart, Math.min(Math.min(lineStart + column - 1, text.length()), limit));
    }

    public String slice(SourceSpan span) {
        requireOwn(span);
        if (span.endOffset() > text.length()) {
            throw new IllegalArgumentException("span " + span + " exceeds source length " + text.length());
        }
        return text.substring(span.startOffset(), span.endOffset());
    }

    /** Formats a span as {@code name:line:column} using derived display positions. */
    public String formatLocation(SourceSpan span) {
        requireOwn(span);
        LineColumn lc = lineColumnAt(span.startOffset());
        return name + ":" + lc.line() + ":" + lc.column();
    }

    private void requireOwn(SourceSpan span) {
        if (span.sourceId() != id) {
            throw new IllegalArgumentException("span from source " + span.sourceId() + " does not belong to source " + id);
        }
    }
}
