package dev.solvik.compiler.diagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects diagnostics for one compilation.
 *
 * <p>A phase records every problem it can recover from, then calls {@link #throwIfErrors()} so the
 * next phase only runs on error-free input. This keeps the pipeline layered: lexing failures never
 * reach the parser, and parser failures never reach the transpiler.
 */
public final class Diagnostics {

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    public void add(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public void error(String code, SourcePos pos, String message) {
        diagnostics.add(Diagnostic.error(code, pos, message));
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(Diagnostic::isError);
    }

    public List<Diagnostic> all() {
        return Collections.unmodifiableList(diagnostics);
    }

    public int size() {
        return diagnostics.size();
    }

    /** Discards the most recent diagnostic; used when a speculative parse is rolled back. */
    public void removeLast() {
        if (!diagnostics.isEmpty()) {
            diagnostics.remove(diagnostics.size() - 1);
        }
    }

    /** Throws when any error has been recorded; call at the end of each phase. */
    public void throwIfErrors() {
        if (hasErrors()) {
            throw new SolvikException(diagnostics);
        }
    }

    /** Renders every diagnostic, one per line, using {@code fileName} in each position. */
    public String format(String fileName) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : diagnostics) {
            sb.append(d.format(fileName)).append(System.lineSeparator());
        }
        return sb.toString();
    }
}
