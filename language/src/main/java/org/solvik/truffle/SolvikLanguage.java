/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.source.Source;
import org.solvik.lowering.LoweredProgram;
import org.solvik.lowering.SolvikLowering;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.source.SourceFile;

/**
 * The Solvik Truffle language (docs/ARCHITECTURE.md). Its parse entry point runs the required
 * pipeline: Solvik source, semicolon-inserting lexical token stream, ANTLR grammar, syntax AST,
 * static semantic analysis, and only then typed lowering to the Truffle AST backend. A program with
 * any compile-time error throws {@link SolvikParseException} before lowering, so no executable call
 * target is produced for ill-typed input.
 */
@TruffleLanguage.Registration(id = SolvikLanguage.ID, name = "Solvik", defaultMimeType = SolvikLanguage.MIME_TYPE, characterMimeTypes = SolvikLanguage.MIME_TYPE, contextPolicy = ContextPolicy.EXCLUSIVE, fileTypeDetectors = SolvikFileDetector.class, //
                website = "https://www.graalvm.org/graalvm-as-a-platform/implement-language/")
public final class SolvikLanguage extends TruffleLanguage<SolvikContext> {

    /** The language id registered with the GraalVM polyglot engine. */
    public static final String ID = "solvik";
    /** The MIME type registered for Solvik source. */
    public static final String MIME_TYPE = "application/x-solvik";

    @Override
    protected SolvikContext createContext(Env env) {
        return new SolvikContext(this, env);
    }

    @Override
    protected boolean patchContext(SolvikContext context, Env newEnv) {
        context.patchContext(newEnv);
        return true;
    }

    @Override
    protected CallTarget parse(ParsingRequest request) {
        Source source = request.getSource();
        SourceFile file = new SourceFile(source.getName(), source.getCharacters().toString());

        SolvikParseResult parseResult = SolvikParser.parse(file);
        if (!parseResult.isSuccess()) {
            throw SolvikParseException.create(source, file, parseResult.diagnostics());
        }
        SemanticResult semanticResult = SolvikSemanticAnalyzer.analyze(parseResult.requireAst());
        if (!semanticResult.isSuccess()) {
            throw SolvikParseException.create(source, file, semanticResult.diagnostics());
        }
        LoweredProgram lowered = SolvikLowering.lower(semanticResult.requireProgram(), source, this);
        return lowered.evalTarget();
    }
}
