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
package org.solvik.semantic;

import java.util.Objects;
import java.util.Optional;
import org.solvik.diagnostic.DiagnosticBag;

/**
 * Result of static semantic analysis: either a fully typed {@link CheckedProgram} with no error
 * diagnostics, or a {@link DiagnosticBag} containing at least one error and no program. The
 * analyzer never returns both, so a program with a compile-time error cannot be lowered or
 * executed.
 */
public final class SemanticResult {

    private final CheckedProgram program;
    private final DiagnosticBag diagnostics;

    private SemanticResult(CheckedProgram program, DiagnosticBag diagnostics) {
        this.program = program;
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        if (program != null && diagnostics.hasErrors()) {
            throw new IllegalStateException("a successful analysis must not carry error diagnostics");
        }
        if (program == null && !diagnostics.hasErrors()) {
            throw new IllegalStateException("a failed analysis must carry at least one error diagnostic");
        }
    }

    static SemanticResult success(CheckedProgram program) {
        return new SemanticResult(Objects.requireNonNull(program, "program"), DiagnosticBag.empty());
    }

    static SemanticResult failure(DiagnosticBag diagnostics) {
        return new SemanticResult(null, diagnostics);
    }

    /** The typed program when no error diagnostic exists; empty otherwise. */
    public Optional<CheckedProgram> program() {
        return Optional.ofNullable(program);
    }

    public DiagnosticBag diagnostics() {
        return diagnostics;
    }

    public boolean isSuccess() {
        return program != null;
    }

    /** The typed program, throwing when analysis produced any error diagnostic. */
    public CheckedProgram requireProgram() {
        if (program == null) {
            throw new IllegalStateException("analysis failed: " + diagnostics.all());
        }
        return program;
    }
}
