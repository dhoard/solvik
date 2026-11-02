package dev.solvik.compiler.diagnostic;

import java.util.Locale;

/**
 * A single compiler diagnostic, located at a source position.
 *
 * <p>Diagnostics carry a stable {@code code} so that tests and tooling can assert on behavior
 * without depending on message wording. The Solvik specification requires aborts and failures to
 * be deterministic ({@code LANGUAGE.md} §10).
 */
public record Diagnostic(Severity severity, String code, String message, SourcePos pos) {

    public enum Severity { ERROR, WARNING }

    public static Diagnostic error(String code, SourcePos pos, String message) {
        return new Diagnostic(Severity.ERROR, code, message, pos);
    }

    public static Diagnostic warning(String code, SourcePos pos, String message) {
        return new Diagnostic(Severity.WARNING, code, message, pos);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /** One-line rendering: {@code file:line:col: error [CODE]: message}. */
    public String format(String fileName) {
        return file(pos, fileName) + ":" + pos.line() + ":" + pos.column() + ": "
                + severity.name().toLowerCase(Locale.ROOT) + " [" + code + "]: " + message;
    }

    /** The position's own file, or the caller's name for a single-source compilation. */
    private static String file(SourcePos pos, String fileName) {
        return pos.file() == null ? fileName : pos.file();
    }
}
