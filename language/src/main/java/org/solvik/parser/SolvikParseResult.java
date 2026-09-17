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
package org.solvik.parser;

import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.DiagnosticBag;

/**
 * Result of a Solvik parse: either a complete {@link CompilationUnitNode} with no error
 * diagnostics, or a {@link DiagnosticBag} containing at least one error and no AST. The parser
 * never returns both, so invalid input cannot produce a partial result for later phases.
 */
public final class SolvikParseResult {

    private final CompilationUnitNode ast;
    private final DiagnosticBag diagnostics;

    private SolvikParseResult(CompilationUnitNode ast, DiagnosticBag diagnostics) {
        this.ast = ast;
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        if (ast != null && diagnostics.hasErrors()) {
            throw new IllegalStateException("a successful parse must not carry error diagnostics");
        }
        if (ast == null && !diagnostics.hasErrors()) {
            throw new IllegalStateException("a failed parse must carry at least one error diagnostic");
        }
    }

    static SolvikParseResult success(CompilationUnitNode ast) {
        return new SolvikParseResult(Objects.requireNonNull(ast, "ast"), DiagnosticBag.empty());
    }

    static SolvikParseResult failure(DiagnosticBag diagnostics) {
        return new SolvikParseResult(null, diagnostics);
    }

    /** The parsed AST when no error diagnostic exists; empty otherwise. */
    public Optional<CompilationUnitNode> ast() {
        return Optional.ofNullable(ast);
    }

    public DiagnosticBag diagnostics() {
        return diagnostics;
    }

    public boolean isSuccess() {
        return ast != null;
    }

    /** The AST, throwing when the parse produced any error diagnostic. */
    public CompilationUnitNode requireAst() {
        if (ast == null) {
            throw new IllegalStateException("parse failed: " + diagnostics.all());
        }
        return ast;
    }
}
