/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.parser;

import java.util.Objects;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.parser.generated.SolvikLexer;
import org.solvik.parser.generated.SolvikParser.CompilationUnitContext;
import org.solvik.source.SourceFile;

/**
 * Entry point of the Solvik front-end parser: {@link SourceFile} in, {@link SolvikParseResult} out.
 *
 * <p>This adapter depends only on Truffle-free types: the syntax AST, the diagnostic framework, and
 * the ANTLR runtime. It has no dependency on executable Truffle nodes, and nothing here lowers or
 * executes Solvik code. The inherited SimpleLanguage parser is never referenced.
 *
 * <p>Error policy: the generated parser uses ANTLR's default error strategy, which records one or
 * more syntax problems and then attempts local recovery solely to continue reporting. The rebuilt
 * tree is consumed only when zero error diagnostics were produced; on any error the result carries
 * diagnostics and no AST, so recovery can never smuggle a partial program into later phases.
 *
 * <p>Statement termination follows docs/LANGUAGE_SPEC.md section 16: the raw lexer stream passes
 * through {@link SemicolonInsertingTokenSource}, which injects synthetic {@code SEMI} tokens at
 * physical line boundaries purely lexically. Parser errors therefore cannot influence where
 * statements terminate.
 */
public final class SolvikParser {

    private SolvikParser() {
    }

    /** Parses an entire Solvik source file. Malformed input produces diagnostics, not exceptions. */
    public static SolvikParseResult parse(SourceFile source) {
        Objects.requireNonNull(source, "source");
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString(source.text(), source.name()));
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(new SemicolonInsertingTokenSource(lexer));
        org.solvik.parser.generated.SolvikParser parser = new org.solvik.parser.generated.SolvikParser(tokens);
        parser.removeErrorListeners();
        SolvikErrorListener listener = new SolvikErrorListener(source);
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);

        CompilationUnitContext tree = parser.compilationUnit();
        DiagnosticBag bag = listener.build();
        if (bag.hasErrors()) {
            return SolvikParseResult.failure(bag);
        }
        CompilationUnitNode ast = new SolvikAstBuilder().build(tree);
        return SolvikParseResult.success(ast);
    }
}
