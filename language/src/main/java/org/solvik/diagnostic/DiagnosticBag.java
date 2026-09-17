/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
