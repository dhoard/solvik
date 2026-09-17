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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.source.SourceCatalog;

/**
 * Result of include resolution: either an include-free, flattened {@link CompilationUnitNode} with
 * its {@link SourceCatalog}, or the collected include/parse diagnostics and the catalog built so
 * far. A failure never exposes a unit, so a partially flattened program can never reach semantic
 * analysis.
 */
public final class IncludeResolutionResult {

    private final CompilationUnitNode unit;
    private final DiagnosticBag diagnostics;
    private final SourceCatalog catalog;
    private final Map<AstNode, FileScope> itemScopes;

    private IncludeResolutionResult(CompilationUnitNode unit, DiagnosticBag diagnostics, SourceCatalog catalog, Map<AstNode, FileScope> itemScopes) {
        this.unit = unit;
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.itemScopes = Collections.unmodifiableMap(new IdentityHashMap<>(Objects.requireNonNull(itemScopes, "itemScopes")));
        if (unit != null && diagnostics.hasErrors()) {
            throw new IllegalStateException("a successful resolution must not carry error diagnostics");
        }
        if (unit == null && !diagnostics.hasErrors()) {
            throw new IllegalStateException("a failed resolution must carry at least one error diagnostic");
        }
    }

    public static IncludeResolutionResult success(CompilationUnitNode unit, SourceCatalog catalog) {
        return success(unit, Map.of(), catalog);
    }

    public static IncludeResolutionResult success(CompilationUnitNode unit, Map<AstNode, FileScope> itemScopes, SourceCatalog catalog) {
        return new IncludeResolutionResult(Objects.requireNonNull(unit, "unit"), DiagnosticBag.empty(), catalog, itemScopes);
    }

    public static IncludeResolutionResult failure(DiagnosticBag diagnostics, SourceCatalog catalog) {
        return new IncludeResolutionResult(null, diagnostics, catalog, Map.of());
    }

    public boolean isSuccess() {
        return unit != null;
    }

    public Optional<CompilationUnitNode> unit() {
        return Optional.ofNullable(unit);
    }

    public CompilationUnitNode requireUnit() {
        if (unit == null) {
            throw new IllegalStateException("include resolution failed: " + diagnostics.all());
        }
        return unit;
    }

    public DiagnosticBag diagnostics() {
        return diagnostics;
    }

    public SourceCatalog catalog() {
        return catalog;
    }

    /**
     * The module/namespace context of every resolved top-level item, keyed by node identity. An item
     * absent from the map belongs to the explicit or implicit default module with no prefixes.
     */
    public Map<AstNode, FileScope> itemScopes() {
        return itemScopes;
    }
}
