package org.solvik.transpiler;

import java.util.List;

public final class CompileException extends Exception {
    private static final long serialVersionUID = 1L;
    private final transient List<Diagnostic> diagnostics;

    public CompileException(List<Diagnostic> diagnostics) {
        super(diagnostics.isEmpty() ? "compilation failed" : diagnostics.get(0).message());
        this.diagnostics = List.copyOf(diagnostics);
    }

    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }
}
