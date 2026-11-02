package dev.solvik.compiler.diagnostic;

import java.util.List;

/** Thrown at the end of a phase that recorded one or more errors. */
public final class SolvikException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<Diagnostic> diagnostics;

    public SolvikException(List<Diagnostic> diagnostics) {
        super(render(diagnostics));
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    private static String render(List<Diagnostic> diagnostics) {
        StringBuilder sb = new StringBuilder("compilation failed with ")
                .append(diagnostics.stream().filter(Diagnostic::isError).count())
                .append(" error(s)");
        for (Diagnostic d : diagnostics) {
            if (d.isError()) {
                sb.append(System.lineSeparator()).append(d.format("<source>"));
            }
        }
        return sb.toString();
    }
}
