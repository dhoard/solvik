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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.IncludeDeclNode;
import org.solvik.ast.declaration.ModuleDeclNode;
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
 * (docs/LANGUAGE_SPEC.md section 20). Each physical file is parsed once, its resolved items are
 * spliced in depth-first, left-to-right order, and a canonical file is expanded at most once per
 * root compilation. The resolver produces no executable nodes and performs no runtime file I/O.
 *
 * <p>Phase 17 also records each file's module declaration and the file-local module prefixes it
 * makes visible through {@code include ... alias p} and unaliased inclusion of a module-declaring
 * file. Every resolved top-level item carries the {@link FileScope} of the physical file that
 * declared it, so semantic analysis can resolve names against the declaring file's module and
 * prefixes. Module prefixes are file-local and non-transitive.
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

    /** A resolved top-level item paired with the physical file's module/namespace context. */
    private static final class Scoped {
        final AstNode node;
        FileScope scope;

        Scoped(AstNode node, FileScope scope) {
            this.node = node;
            this.scope = scope;
        }
    }

    private final IncludeSourceAccess access;
    private final DiagnosticBag.Builder diagnostics = DiagnosticBag.builder();
    private final Map<URI, State> states = new HashMap<>();
    /** Declared module name per canonical file; a {@code null} value means the default module. */
    private final Map<URI, String> moduleByKey = new HashMap<>();
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

    private IncludeResolutionResult run(CompilationUnitNode root) {
        LoadedSource rootSource = access.root();
        register(rootSource.file());
        List<Scoped> items = expand(rootSource, root);
        SourceCatalog builtCatalog = catalog.build();
        DiagnosticBag bag = diagnostics.build();
        if (failed || bag.hasErrors()) {
            return IncludeResolutionResult.failure(bag, builtCatalog);
        }
        Map<AstNode, FileScope> itemScopes = new IdentityHashMap<>();
        List<AstNode> nodes = new ArrayList<>(items.size());
        for (Scoped scoped : items) {
            nodes.add(scoped.node);
            itemScopes.put(scoped.node, scoped.scope);
        }
        return IncludeResolutionResult.success(new CompilationUnitNode(nodes, root.span()), itemScopes, builtCatalog);
    }

    private List<Scoped> expand(LoadedSource loaded, CompilationUnitNode preparsed) {
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

        String moduleName = validateModule(parsed);
        moduleByKey.put(key, moduleName);

        List<Scoped> output = new ArrayList<>();
        Map<String, String> prefixes = new LinkedHashMap<>();
        if (moduleName != null) {
            // Reserve the file's own module name before binding aliases, so an alias cannot silently
            // shadow it; a file may always reference its own module through its declared name.
            prefixes.put(moduleName, moduleName);
        }
        boolean success = true;
        for (AstNode item : parsed.items()) {
            if (!(item instanceof IncludeDeclNode include)) {
                output.add(new Scoped(item, null));
                continue;
            }
            if (!expandInclude(loaded, include, output, prefixes)) {
                success = false;
            }
        }
        FileScope scope = new FileScope(moduleName, prefixes);
        for (Scoped scoped : output) {
            if (scoped.scope == null) {
                scoped.scope = scope;
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

    /** Resolves the optional module declaration of a physical file, reporting an invalid name. */
    private String validateModule(CompilationUnitNode parsed) {
        Optional<ModuleDeclNode> declaration = parsed.moduleDeclaration();
        if (declaration.isEmpty()) {
            return null;
        }
        ModuleDeclNode module = declaration.get();
        if (!ModuleNames.isValid(module.name())) {
            error(DiagnosticCode.RESOL_MODULE_INVALID_NAME, module.span(), //
                            "invalid module name '" + module.name() + "'; expected lowercase letters and digits joined by single underscores");
            return null;
        }
        return module.name();
    }

    /** Resolves one include directive, appending its expansion; returns whether it succeeded. */
    private boolean expandInclude(LoadedSource including, IncludeDeclNode include, List<Scoped> output, Map<String, String> prefixes) {
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
        List<Scoped> targetItems = expand(target, null);
        if (states.get(target.canonicalKey()) != State.COMPLETE) {
            return false;
        }
        String targetModule = moduleByKey.get(target.canonicalKey());

        if (include.hasAlias()) {
            String alias = include.alias();
            if (!ModuleNames.isValid(alias)) {
                error(DiagnosticCode.RESOL_MODULE_INVALID_NAME, include.span(), //
                                "invalid alias name '" + alias + "'; expected lowercase letters and digits joined by single underscores");
                return false;
            }
            if (targetModule == null) {
                error(DiagnosticCode.RESOL_ALIAS_DEFAULT_MODULE, include.span(), //
                                "cannot alias a file in the default module; declare 'module' in the included file");
                return false;
            }
            if (prefixes.containsKey(alias)) {
                error(DiagnosticCode.RESOL_ALIAS_DUPLICATE, include.span(), "prefix '" + alias + "' is already bound in this file");
                return false;
            }
            prefixes.put(alias, targetModule);
            output.addAll(targetItems);
            return true;
        }

        if (targetModule != null) {
            prefixes.putIfAbsent(targetModule, targetModule);
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
