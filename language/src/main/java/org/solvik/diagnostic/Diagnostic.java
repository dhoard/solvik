/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.diagnostic;

import java.util.Objects;
import java.util.Optional;
import org.solvik.source.SourceSpan;

/**
 * A single compile-time diagnostic: stable code, severity, primary source span, message, and
 * optional expected/found values. Instances are immutable.
 */
public final class Diagnostic {

    private final DiagnosticCode code;
    private final DiagnosticSeverity severity;
    private final SourceSpan span;
    private final String message;
    private final String expected;
    private final String found;

    public Diagnostic(DiagnosticCode code, DiagnosticSeverity severity, SourceSpan span, String message, String expected, String found) {
        this.code = Objects.requireNonNull(code);
        this.severity = Objects.requireNonNull(severity);
        this.span = Objects.requireNonNull(span);
        this.message = Objects.requireNonNull(message);
        this.expected = expected;
        this.found = found;
    }

    public static Diagnostic error(DiagnosticCode code, SourceSpan span, String message) {
        return new Diagnostic(code, DiagnosticSeverity.ERROR, span, message, null, null);
    }

    public static Diagnostic expectedFound(DiagnosticCode code, SourceSpan span, String message, String expected, String found) {
        return new Diagnostic(code, DiagnosticSeverity.ERROR, span, message, expected, found);
    }

    public DiagnosticCode code() {
        return code;
    }

    public DiagnosticSeverity severity() {
        return severity;
    }

    public SourceSpan span() {
        return span;
    }

    public String message() {
        return message;
    }

    public Optional<String> expected() {
        return Optional.ofNullable(expected);
    }

    public Optional<String> found() {
        return Optional.ofNullable(found);
    }

    public boolean isError() {
        return severity == DiagnosticSeverity.ERROR;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(severity).append(" ").append(code.stableCode()).append(": ").append(message);
        if (expected != null) {
            sb.append(" (expected: ").append(expected);
            if (found != null) {
                sb.append(", found: ").append(found);
            }
            sb.append(")");
        }
        sb.append(" at ").append(span);
        return sb.toString();
    }
}
