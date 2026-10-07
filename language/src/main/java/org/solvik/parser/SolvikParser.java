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
import java.util.List;
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
 * through {@link PhysicalLineTokenSource}, which places {@code NEWLINE} boundary tokens at physical
 * line boundaries purely lexically. Parser errors therefore cannot influence where statements
 * terminate.
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
        if (parsed[0] == null) {
            // The thread ended without producing a result and without overflowing its stack, so a
            // stage failed outright. That is a compiler defect, and it is reported as one rather than
            // traded for a diagnostic that would blame the program for the front end's own failure.
            throw new IllegalStateException("parse of " + source.name() + " produced no result");
        }
        return parsed[0];
    }

    /** The whole-file diagnostic reported for a source the parser cannot recurse deeply enough to read. */
    private static Diagnostic tooDeep(SourceFile source) {
        return Diagnostic.error(DiagnosticCode.PARSER_NESTING_TOO_DEEP, SourceSpan.of(source.id(), 0, source.textLength()), NESTING_TOO_DEEP_MESSAGE);
    }

    /**
     * Fast first-stage parse when it completes cleanly; the reporting parse otherwise. Both stages
     * parse phrase structure only. The physical-line rules of section 16 are then checked over a
     * freshly lexed token stream, because a program can parse perfectly while placing a brace or a
     * clause keyword on the wrong physical line; those violations are added to whatever the parse
     * itself reported rather than replacing it.
     */
    private static SolvikParseResult parseFile(SourceFile source) {
        SolvikParseResult parsed = tryFirstStageParse(source);
        if (parsed == null) {
            parsed = parseForReporting(source);
        }
        List<Diagnostic> layout = PhysicalLineRules.check(source, new CommonTokenStream(new PhysicalLineTokenSource(newLexer(source))));
        if (layout.isEmpty() || !parsed.isSuccess()) {
            return parsed;
        }
        DiagnosticBag.Builder builder = DiagnosticBag.builder();
        parsed.diagnostics().all().forEach(builder::add);
        layout.forEach(builder::add);
        return SolvikParseResult.failure(builder.build());
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
            CommonTokenStream tokens = new CommonTokenStream(new PhysicalLineTokenSource(lexer));
            org.solvik.parser.generated.SolvikParser parser = new org.solvik.parser.generated.SolvikParser(tokens);
            parser.removeErrorListeners();
            parser.setErrorHandler(new BailErrorStrategy());
            parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
            CompilationUnitContext tree = parser.compilationUnit();
            if (lexerErrors.sawError() || hasRemovedSyntax(tree)) {
                // Retired syntax defers to the reporting stage, which owns the diagnostic; the fast
                // stage never builds an AST around a rejected construct.
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
        CommonTokenStream tokens = new CommonTokenStream(new PhysicalLineTokenSource(lexer));
        org.solvik.parser.generated.SolvikParser parser = new org.solvik.parser.generated.SolvikParser(tokens);
        parser.removeErrorListeners();
        SolvikErrorListener listener = new SolvikErrorListener(source);
        lexer.addErrorListener(listener);
        parser.addErrorListener(listener);

        CompilationUnitContext tree = parser.compilationUnit();
        DiagnosticBag bag = listener.build();
        List<Diagnostic> removed = removedSyntaxDiagnostics(tree, source);
        if (!removed.isEmpty()) {
            DiagnosticBag.Builder builder = DiagnosticBag.builder();
            bag.all().forEach(builder::add);
            removed.forEach(builder::add);
            return SolvikParseResult.failure(builder.build());
        }
        if (bag.hasErrors()) {
            return SolvikParseResult.failure(bag);
        }
        CompilationUnitNode ast = new SolvikAstBuilder(source).build(tree);
        return SolvikParseResult.success(ast);
    }

    /**
     * The {@code removed*} productions are matched by the grammar for exactly one reason: so a program
     * written against the retired syntax model is named at its own first token with the replacement
     * spelled out, instead of failing as a generic syntax error a few tokens later. The constructs
     * have no AST node and no execution path; this detection is the whole of them
     * (docs/LANGUAGE_SPEC.md sections 2, 6, 7, 9, 17, 20).
     */
    private static boolean hasRemovedSyntax(org.antlr.v4.runtime.tree.ParseTree tree) {
        return !removedSyntaxDiagnostics(tree, null).isEmpty();
    }

    /** One diagnostic per retired construct, anchored at the construct's first token. */
    private static List<Diagnostic> removedSyntaxDiagnostics(org.antlr.v4.runtime.tree.ParseTree tree, SourceFile source) {
        List<Diagnostic> found = new java.util.ArrayList<>();
        org.antlr.v4.runtime.tree.ParseTreeWalker.DEFAULT.walk(new org.antlr.v4.runtime.tree.ParseTreeListener() {
            @Override
            public void enterEveryRule(org.antlr.v4.runtime.ParserRuleContext ctx) {
                Diagnosis diagnosis = diagnosisFor(ctx);
                if (diagnosis == null) {
                    return;
                }
                SourceSpan span = source == null ? SourceSpan.of(0, 0, 0) : tokenSpan(ctx.getStart(), source);
                found.add(Diagnostic.error(diagnosis.code(), span, diagnosis.message()));
            }

            @Override
            public void exitEveryRule(org.antlr.v4.runtime.ParserRuleContext ctx) {
            }

            @Override
            public void visitErrorNode(org.antlr.v4.runtime.tree.ErrorNode node) {
            }

            @Override
            public void visitTerminal(org.antlr.v4.runtime.tree.TerminalNode node) {
            }
        }, tree);
        return found;
    }

    /** The diagnostic a retired construct earns, or {@code null} for every live construct. */
    private static Diagnosis diagnosisFor(org.antlr.v4.runtime.ParserRuleContext ctx) {
        if (ctx instanceof org.solvik.parser.generated.SolvikParser.RemovedForStmtContext) {
            return new Diagnosis(DiagnosticCode.PARSER_REMOVED_THREE_CLAUSE_FOR,
                    "the three-clause `for` syntax was removed in 2026.11-draft; use a range `for` over an "
                    + "integer range or a scope block around a `while` loop");
        }
        if (ctx instanceof org.solvik.parser.generated.SolvikParser.RemovedFuncMemberContext) {
            return new Diagnosis(DiagnosticCode.PARSER_REMOVED_DECLARATION,
                    "'func' cannot declare a class or interface member; write 'method name(...) { ... }'");
        }
        if (ctx instanceof org.solvik.parser.generated.SolvikParser.RemovedInferredLocalDeclContext) {
            return new Diagnosis(DiagnosticCode.PARSER_REMOVED_DECLARATION,
                    "a local declaration must write its type; write 'var name: Type = expression'");
        }
        if (ctx instanceof org.solvik.parser.generated.SolvikParser.RemovedDelegateVarContext) {
            return new Diagnosis(DiagnosticCode.PARSER_REMOVED_DECLARATION,
                    "'delegate var' is not a declaration; write 'delegate name: InterfaceType'");
        }
        if (ctx instanceof org.solvik.parser.generated.SolvikParser.RemovedModuleHeaderContext) {
            return new Diagnosis(DiagnosticCode.PARSER_REMOVED_DECLARATION,
                    "a module is a braced block; write 'module Name { ... }' and put declarations inside it");
        }
        if (ctx instanceof org.solvik.parser.generated.SolvikParser.RemovedIncludeAliasContext) {
            return new Diagnosis(DiagnosticCode.PARSER_REMOVED_DECLARATION,
                    "an include includes source and binds no name; remove the 'alias' suffix and name the module "
                    + "with a 'module Name { ... }' block");
        }
        if (ctx instanceof org.solvik.parser.generated.SolvikParser.RemovedClassModifierPrefixContext) {
            return new Diagnosis(DiagnosticCode.PARSER_REMOVED_DECLARATION,
                    "a class modifier follows the 'class' keyword; write 'class mutable Name' or 'class abstract Name'");
        }
        if (ctx instanceof org.solvik.parser.generated.SolvikParser.RemovedMemberModifierPrefixContext) {
            return new Diagnosis(DiagnosticCode.PARSER_REMOVED_DECLARATION,
                    "a member modifier follows the declaration keyword; write 'method static', 'method mutable', "
                    + "'method override mutable', or 'var static mutable'");
        }
        if (ctx instanceof org.solvik.parser.generated.SolvikParser.RemovedAnonymousFunctionExprContext) {
            return new Diagnosis(DiagnosticCode.PARSER_REMOVED_FUNCTION_VALUE,
                    "function values were removed; declare a 'func' and call it directly");
        }
        if (ctx instanceof org.solvik.parser.generated.SolvikParser.RemovedFunctionTypeRefContext) {
            return new Diagnosis(DiagnosticCode.PARSER_REMOVED_FUNCTION_VALUE,
                    "function types were removed; declare a 'func' and call it directly");
        }
        return null;
    }

    /** A retired construct's stable code and replacement-naming message. */
    private record Diagnosis(DiagnosticCode code, String message) {
    }

    /** Character span of a token; mirrors the error listener's anchoring of parser diagnostics. */
    private static SourceSpan tokenSpan(org.antlr.v4.runtime.Token token, SourceFile source) {
        int start = token.getStartIndex();
        int stop = Math.max(token.getStopIndex(), start - 1);
        return SourceSpan.of(source.id(), start, Math.min(stop + 1, source.textLength()));
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
