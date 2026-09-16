/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.diagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An ordered, immutable collection of diagnostics with query helpers. The front end collects
 * diagnostics here; downstream phases must check {@link #hasErrors()} before consuming an AST.
 */
public final class DiagnosticBag {

    private final List<Diagnostic> diagnostics;
    private final boolean hasErrors;

    private DiagnosticBag(List<Diagnostic> diagnostics, boolean hasErrors) {
        this.diagnostics = diagnostics;
        this.hasErrors = hasErrors;
    }

    public static DiagnosticBag empty() {
        return new DiagnosticBag(List.of(), false);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Diagnostics in the order they were added. */
    public List<Diagnostic> all() {
        return diagnostics;
    }

    public boolean isEmpty() {
        return diagnostics.isEmpty();
    }

    public int size() {
        return diagnostics.size();
    }

    public boolean hasErrors() {
        return hasErrors;
    }

    /** A bag builder. Adding to a built bag through another builder reference is not permitted. */
    public static final class Builder {

        private final List<Diagnostic> collected = new ArrayList<>();
        private boolean errors;
        private boolean built;

        private Builder() {
        }

        public void add(Diagnostic diagnostic) {
            requireUnbuilt();
            collected.add(diagnostic);
            if (diagnostic.isError()) {
                errors = true;
            }
        }

        public boolean hasErrors() {
            requireUnbuilt();
            return errors;
        }

        public DiagnosticBag build() {
            requireUnbuilt();
            built = true;
            return new DiagnosticBag(Collections.unmodifiableList(new ArrayList<>(collected)), errors);
        }

        private void requireUnbuilt() {
            if (built) {
                throw new IllegalStateException("DiagnosticBag.Builder already built");
            }
        }
    }
}
