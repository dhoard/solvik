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
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.generated.SolvikLexer;
import org.solvik.parser.generated.SolvikParser.CompilationUnitContext;
import org.solvik.source.SourceFile;
import org.solvik.source.SourceSpan;

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
 * <p>Prediction strategy: ANTLR's full-context ({@code LL}) prediction is super-linear on nested
 * constructs that never need it, so a valid program that nests {@code if} or {@code switch} bodies
 * a few hundred levels deep becomes an effectively infinite compile. The common case therefore runs
 * on ANTLR's standard first stage ({@code SLL} prediction with a bail-out error strategy) and its
 * tree is used when it completes cleanly. Any bail-out or lexical problem re-runs the file with the
 * reporting configuration (full {@code LL} prediction and the default error strategy), so every
 * rejected program produces exactly the reporting pass's diagnostics. Both passes produce
 * structurally identical trees for every accepted program the grammar can parse; see
 * docs/FRONTEND_ROBUSTNESS_AUDIT.md.
 *
 * <p>Recursion capacity: right-recursive rules (a parenthesized expression, a call chain, a variant
 * pattern, a type-argument list) consume one Java frame per nesting level in the generated parser
 * and again in {@link SolvikAstBuilder}. The front-end body therefore runs on a dedicated thread
 * with a reserved stack instead of the caller's, and a {@link StackOverflowError} that still escapes
 * becomes a {@link DiagnosticCode#PARSER_NESTING_TOO_DEEP} error. Malformed or pathological input
 * must be a source-located compile-time diagnostic, never a VM resource failure.
 *
 * <p>Statement termination follows docs/LANGUAGE_SPEC.md section 16: the raw lexer stream passes
 * through {@link SemicolonInsertingTokenSource}, which injects synthetic {@code SEMI} tokens at
 * physical line boundaries purely lexically. Parser errors therefore cannot influence where
 * statements terminate.
 */
public final class SolvikParser {

    /**
     * Reserved stack for one front-end parse. The stack is lazily committed, so the cost is virtual
     * address space rather than time or resident memory: starting and joining a thread with this
     * reservation measures the same as the platform default.
     */
    private static final int PARSE_STACK_BYTES = 128 * 1024 * 1024;

    /** Message reported when nesting exceeds the compiler's available recursion capacity. */
    private static final String NESTING_TOO_DEEP_MESSAGE = "source nests constructs more deeply than the compiler can process; reduce the nesting depth";

    private SolvikParser() {
    }

    /** Parses an entire Solvik source file. Malformed input produces diagnostics, not exceptions. */
    public static SolvikParseResult parse(SourceFile source) {
        Objects.requireNonNull(source, "source");
        return parseOnFrontEndStack(source);
    }

    /**
     * Runs the front end on a thread with reserved recursion capacity and converts an escaping
     * {@link StackOverflowError} into a {@link DiagnosticCode#PARSER_NESTING_TOO_DEEP} diagnostic.
     * Interruption is a host condition rather than a property of the source, so it is reported as an
     * interruption of the compilation rather than as a source diagnostic.
     */
    private static SolvikParseResult parseOnFrontEndStack(SourceFile source) {
        final SolvikParseResult[] parsed = new SolvikParseResult[1];
        final boolean[] overflowed = new boolean[1];
        Thread parserThread = new Thread(null, () -> {
            try {
                parsed[0] = parseFile(source);
            } catch (StackOverflowError overflow) {
                overflowed[0] = true;
            }
        }, "solvik-parse", PARSE_STACK_BYTES);
        try {
            parserThread.start();
            parserThread.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("parse of " + source.name() + " was interrupted", interrupted);
        }
        if (overflowed[0]) {
            DiagnosticBag.Builder builder = DiagnosticBag.builder();
            builder.add(tooDeep(source));
            return SolvikParseResult.failure(builder.build());
        }
        return parsed[0];
    }

    /** The whole-file diagnostic reported for a source the parser cannot recurse deeply enough to read. */
    private static Diagnostic tooDeep(SourceFile source) {
        return Diagnostic.error(DiagnosticCode.PARSER_NESTING_TOO_DEEP, SourceSpan.of(source.id(), 0, source.textLength()), NESTING_TOO_DEEP_MESSAGE);
    }

    /** Fast first-stage parse when it completes cleanly; the reporting parse otherwise. */
    private static SolvikParseResult parseFile(SourceFile source) {
        SolvikParseResult fast = tryFirstStageParse(source);
        if (fast != null) {
            return fast;
        }
        return parseForReporting(source);
    }

    /**
     * The fast stage: {@code SLL} prediction plus a bail-out error strategy, whose tree is used only
     * when the pass completes without bailing and without a single lexical problem. Returns
     * {@code null} when the reporting pass must run instead. Any unexpected runtime failure of this
     * stage also defers to the reporting pass, which is the authority for both accepted and rejected
     * programs, so the fast stage can only ever make parsing cheaper.
     */
    private static SolvikParseResult tryFirstStageParse(SourceFile source) {
        try {
            SolvikLexer lexer = newLexer(source);
            LexerErrorFlag lexerErrors = new LexerErrorFlag();
            lexer.addErrorListener(lexerErrors);
            CommonTokenStream tokens = new CommonTokenStream(new SemicolonInsertingTokenSource(lexer));
            org.solvik.parser.generated.SolvikParser parser = new org.solvik.parser.generated.SolvikParser(tokens);
            parser.removeErrorListeners();
            parser.setErrorHandler(new BailErrorStrategy());
            parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
            CompilationUnitContext tree = parser.compilationUnit();
            if (lexerErrors.sawError()) {
                return null;
            }
            return SolvikParseResult.success(new SolvikAstBuilder(source).build(tree));
        } catch (RuntimeException | StackOverflowError deferred) {
            // A bail-out is reported as a wrapped runtime failure; a stack overflow here means the
            // file is out of recursion capacity, which the reporting pass diagnoses authoritatively.
            return null;
        }
    }

    /** The reporting stage: full-context prediction and ANTLR's default recovering error strategy. */
    private static SolvikParseResult parseForReporting(SourceFile source) {
        SolvikLexer lexer = newLexer(source);
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
        CompilationUnitNode ast = new SolvikAstBuilder(source).build(tree);
        return SolvikParseResult.success(ast);
    }

    private static SolvikLexer newLexer(SourceFile source) {
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString(source.text(), source.name()));
        lexer.removeErrorListeners();
        return lexer;
    }

    /** Records only whether the lexer reported anything, so the fast stage can defer on any lexical error. */
    private static final class LexerErrorFlag extends org.antlr.v4.runtime.BaseErrorListener {
        private boolean sawError;

        @Override
        public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, org.antlr.v4.runtime.RecognitionException e) {
            sawError = true;
        }

        boolean sawError() {
            return sawError;
        }
    }
}
