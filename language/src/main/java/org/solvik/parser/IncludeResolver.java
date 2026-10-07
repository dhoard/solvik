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

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.IncludeDeclNode;
import org.solvik.ast.declaration.ModuleBlockNode;
import org.solvik.ast.expression.RawStringLiteralNode;
import org.solvik.ast.expression.StringLiteralNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.source.SourceCatalog;
import org.solvik.source.SourceFile;
import org.solvik.source.SourceSpan;
import org.solvik.source.StringEscapes;

/**
 * Resolves compile-time {@code include} directives into one flattened syntax AST
 * (docs/LANGUAGE_SPEC.md section 20). Each physical file is parsed once, its items are spliced in
 * depth-first, left-to-right order at the position of the directive that included it, and a canonical
 * file is expanded at most once per root compilation. The resolver produces no executable nodes and
 * performs no runtime file I/O.
 *
 * <p>A file is a source container rather than a namespace, so splicing preserves each file's
 * {@code module Name { ... }} blocks exactly as written: two blocks with one name — in one file or in
 * two included files — become two blocks of the one module, which semantic analysis merges. An
 * include binds no name, so the resolver records no module or prefix scope; the module a declaration
 * belongs to is structural, through the block that contains it.
 *
 * <p>The traversal keeps a distinct {@code ACTIVE}, {@code COMPLETE}, or {@code FAILED} state per
 * canonical source. An {@code ACTIVE} target is a cycle, a {@code COMPLETE} target is a no-op, and a
 * previously failed target propagates failure without repeating its diagnostics. After a failed
 * include the walk continues with later siblings so one compilation can report independent failures.
 */
public final class IncludeResolver {

    /** Per-canonical-source expansion state. */
    private enum State {
        ACTIVE,
        COMPLETE,
        FAILED
    }

    private final IncludeSourceAccess access;
    private final DiagnosticBag.Builder diagnostics = DiagnosticBag.builder();
    private final Map<URI, State> states = new HashMap<>();
    private final Deque<LoadedSource> activeStack = new ArrayDeque<>();
    private final SourceCatalog.Builder catalog = SourceCatalog.builder();
    private final Set<Integer> catalogIds = new HashSet<>();
    private boolean failed;

    private IncludeResolver(IncludeSourceAccess access) {
        this.access = Objects.requireNonNull(access, "access");
    }

    /** Expands {@code root}, which has already been parsed, into one include-free unit. */
    public static IncludeResolutionResult resolve(CompilationUnitNode root, IncludeSourceAccess access) {
        Objects.requireNonNull(root, "root");
        return new IncludeResolver(access).run(root);
    }

    /**
     * Validates the {@code module Name} blocks of a parsed program and returns the
     * {@code RESOL_MODULE_INVALID_NAME} diagnostics for the names that break the module naming rule;
     * the list is empty when every name is valid. It performs no filesystem access, so a caller that
     * compiles an include-free root without an {@link IncludeSourceAccess} still reports the same
     * diagnostics at the same stage as an expanded program.
     */
    public static List<Diagnostic> invalidModuleDeclarations(CompilationUnitNode unit) {
        Objects.requireNonNull(unit, "unit");
        List<Diagnostic> reported = new ArrayList<>();
        for (ModuleBlockNode module : unit.modules()) {
            if (!ModuleNames.isValid(module.name())) {
                reported.add(Diagnostic.error(DiagnosticCode.RESOL_MODULE_INVALID_NAME, module.span(), //
                                ModuleNames.invalidNameMessage(module.name())));
            }
        }
        return List.copyOf(reported);
    }

    private IncludeResolutionResult run(CompilationUnitNode root) {
        LoadedSource rootSource = access.root();
        register(rootSource.file());
        List<AstNode> nodes = expand(rootSource, root);
        SourceCatalog builtCatalog = catalog.build();
        DiagnosticBag bag = diagnostics.build();
        if (failed || bag.hasErrors()) {
            return IncludeResolutionResult.failure(bag, builtCatalog);
        }
        return IncludeResolutionResult.success(new CompilationUnitNode(nodes, root.span()), builtCatalog);
    }

    private List<AstNode> expand(LoadedSource loaded, CompilationUnitNode preparsed) {
        URI key = loaded.canonicalKey();
        State state = states.get(key);
        if (state == State.COMPLETE) {
            return List.of();
        }
        if (state == State.FAILED) {
            failed = true;
            return List.of();
        }
        states.put(key, State.ACTIVE);
        activeStack.addLast(loaded);
        register(loaded.file());

        CompilationUnitNode parsed = preparsed;
        if (parsed == null) {
            SolvikParseResult parseResult = SolvikParser.parse(loaded.file());
            if (!parseResult.isSuccess()) {
                for (Diagnostic diagnostic : parseResult.diagnostics().all()) {
                    diagnostics.add(diagnostic);
                }
                failed = true;
                activeStack.removeLast();
                states.put(key, State.FAILED);
                return List.of();
            }
            parsed = parseResult.requireAst();
        }

        // The file's own module names are validated here, where an invalid name is reported once for
        // every physical file the program was built from.
        boolean success = true;
        for (Diagnostic invalid : invalidModuleDeclarations(parsed)) {
            diagnostics.add(invalid);
            success = false;
        }

        List<AstNode> output = new ArrayList<>();
        for (AstNode item : parsed.items()) {
            if (item instanceof IncludeDeclNode include) {
                if (!expandInclude(loaded, include, output)) {
                    success = false;
                }
            } else {
                output.add(item);
            }
        }

        activeStack.removeLast();
        if (success) {
            states.put(key, State.COMPLETE);
        } else {
            states.put(key, State.FAILED);
            failed = true;
        }
        return output;
    }

    /** Resolves one include directive, appending its expansion; returns whether it succeeded. */
    private boolean expandInclude(LoadedSource including, IncludeDeclNode include, List<AstNode> output) {
        String decoded;
        if (include.pathLiteral() instanceof StringLiteralNode string) {
            Optional<String> invalid = StringEscapes.invalidEscape(string.lexeme());
            if (invalid.isPresent()) {
                error(DiagnosticCode.LEXER_INVALID_ESCAPE, string.span(), "invalid escape sequence in include path: " + invalid.get());
                return false;
            }
            decoded = StringEscapes.unescape(string.lexeme());
        } else {
            decoded = ((RawStringLiteralNode) include.pathLiteral()).value();
        }
        if (decoded.isEmpty() || !decoded.endsWith(".sol")) {
            error(DiagnosticCode.RESOL_INCLUDE_INVALID_PATH, include.pathLiteral().span(), "include path must name a non-empty .sol file: '" + decoded + "'");
            return false;
        }
        IncludeSourceAccess.LoadResult load = access.load(including, decoded);
        if (load instanceof IncludeSourceAccess.Failure failure) {
            error(failure.code(), spanFor(failure.code(), include), failure.message());
            return false;
        }
        LoadedSource target = ((IncludeSourceAccess.Success) load).source();
        register(target.file());
        State targetState = states.get(target.canonicalKey());
        if (targetState == State.ACTIVE) {
            error(DiagnosticCode.RESOL_INCLUDE_CYCLE, include.span(), cycleMessage(target));
            return false;
        }
        if (targetState == State.FAILED) {
            // A prior expansion of this physical file already reported its diagnostics.
            return false;
        }
        List<AstNode> targetItems = expand(target, null);
        if (states.get(target.canonicalKey()) != State.COMPLETE) {
            return false;
        }
        output.addAll(targetItems);
        return true;
    }

    private void register(SourceFile file) {
        if (catalogIds.add(file.id())) {
            catalog.add(file);
        }
    }

    private static SourceSpan spanFor(DiagnosticCode code, IncludeDeclNode include) {
        return code == DiagnosticCode.RESOL_INCLUDE_INVALID_PATH ? include.pathLiteral().span() : include.span();
    }

    private String cycleMessage(LoadedSource target) {
        List<String> chain = new ArrayList<>();
        boolean started = false;
        for (LoadedSource source : activeStack) {
            if (source.canonicalKey().equals(target.canonicalKey())) {
                started = true;
            }
            if (started) {
                chain.add(source.file().name());
            }
        }
        chain.add(target.file().name());
        return "include cycle: " + String.join(" -> ", chain);
    }

    private void error(DiagnosticCode code, SourceSpan span, String message) {
        diagnostics.add(Diagnostic.error(code, span, message));
    }
}
