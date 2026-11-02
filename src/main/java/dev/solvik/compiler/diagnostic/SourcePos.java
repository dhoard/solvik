package dev.solvik.compiler.diagnostic;

/**
 * A position in a Solvik source file.
 *
 * @param offset zero-based character offset from the start of the source
 * @param line   one-based line number
 * @param column one-based column number
 * @param file   the file the position belongs to, or {@code null} when a single source is compiled
 *               from memory; a multi-file program needs it to attribute each diagnostic (§3,
 *               "Source files")
 */
public record SourcePos(int offset, int line, int column, String file) {

    public static final SourcePos START = new SourcePos(0, 1, 1);

    /** A position in a single source compiled from memory. */
    public SourcePos(int offset, int line, int column) {
        this(offset, line, column, null);
    }

    @Override
    public String toString() {
        return line + ":" + column;
    }
}
