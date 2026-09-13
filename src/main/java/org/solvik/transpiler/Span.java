package org.solvik.transpiler;

/** A half-open source range with a stable diagnostic location. */
public record Span(String file, int start, int end, int line, int column) {
    public static Span synthetic(String file) {
        return new Span(file, 0, 0, 1, 1);
    }
}
