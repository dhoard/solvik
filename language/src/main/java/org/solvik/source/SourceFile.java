/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
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

    private final String name;
    private final String text;
    /** Start offset of each physical line; index i corresponds to line number i + 1. */
    private final int[] lineStarts;

    public SourceFile(String name, String text) {
        this.name = Objects.requireNonNull(name);
        this.text = Objects.requireNonNull(text);
        this.lineStarts = computeLineStarts(text);
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
        if (span.endOffset() > text.length()) {
            throw new IllegalArgumentException("span " + span + " exceeds source length " + text.length());
        }
        return text.substring(span.startOffset(), span.endOffset());
    }

    /** Formats a span as {@code name:line:column} using derived display positions. */
    public String formatLocation(SourceSpan span) {
        LineColumn lc = lineColumnAt(span.startOffset());
        return name + ":" + lc.line() + ":" + lc.column();
    }
}
