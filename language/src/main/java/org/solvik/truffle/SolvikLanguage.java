/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle;

import java.util.Map;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextPolicy;
import com.oracle.truffle.api.source.Source;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.lowering.LoweredProgram;
import org.solvik.lowering.SolvikLowering;
import org.solvik.parser.FileScope;
import org.solvik.parser.IncludeResolutionResult;
import org.solvik.parser.IncludeResolver;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.source.SourceCatalog;
import org.solvik.source.SourceFile;

/**
 * The Solvik Truffle language (docs/ARCHITECTURE.md). Its parse entry point runs the required
 * pipeline: Solvik source, semicolon-inserting lexical token stream, ANTLR grammar, syntax AST,
 * compile-time include resolution, static semantic analysis, and only then typed lowering to the
 * Truffle AST backend. A program with any compile-time error throws {@link SolvikParseException}
 * before lowering, so no executable call target is produced for ill-typed input.
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
        SourceFile rootFile = new SourceFile(source.getName(), source.getCharacters().toString());

        SolvikParseResult parseResult = SolvikParser.parse(rootFile);
        if (!parseResult.isSuccess()) {
            throw SolvikParseException.create(source, rootFile, parseResult.diagnostics());
        }
        CompilationUnitNode rootAst = parseResult.requireAst();

        SourceCatalog catalog;
        Map<Integer, Source> sourcesById;
        CompilationUnitNode unit;
        Map<AstNode, FileScope> itemScopes;
        if (!rootAst.hasUnresolvedIncludes()) {
            // A root without includes performs no filesystem access and requires no I/O permission.
            catalog = SourceCatalog.singleton(rootFile);
            sourcesById = Map.of(0, source);
            unit = rootAst;
            itemScopes = Map.of();
        } else {
            TruffleIncludeSourceAccess access = new TruffleIncludeSourceAccess(env(), source, rootFile);
            IncludeResolutionResult resolved = IncludeResolver.resolve(rootAst, access);
            catalog = resolved.catalog();
            sourcesById = access.sourcesById();
            if (!resolved.isSuccess()) {
                throw SolvikParseException.create(catalog, sourcesById, resolved.diagnostics());
            }
            unit = resolved.requireUnit();
            itemScopes = resolved.itemScopes();
        }

        SemanticResult semanticResult = SolvikSemanticAnalyzer.analyze(unit, itemScopes);
        if (!semanticResult.isSuccess()) {
            throw SolvikParseException.create(catalog, sourcesById, semanticResult.diagnostics());
        }
        LoweredProgram lowered = SolvikLowering.lower(semanticResult.requireProgram(), sourcesById, this);
        return lowered.evalTarget();
    }

    private static Env env() {
        SolvikContext context = getCurrentContext(SolvikLanguage.class);
        if (context == null) {
            throw new IllegalStateException("no Solvik context is active during parse");
        }
        return context.getEnv();
    }
}
