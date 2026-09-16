/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
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
