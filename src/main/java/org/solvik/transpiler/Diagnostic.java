package org.solvik.transpiler;

public record Diagnostic(String code, String message, Span span) {
    @Override
    public String toString() {
        return span.file() + ":" + span.line() + ":" + span.column()
                + ": error " + code + ": " + message;
    }
}
