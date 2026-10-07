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
package org.solvik.ast;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.declaration.DeclarationNode;
import org.solvik.ast.declaration.IncludeDeclNode;
import org.solvik.ast.declaration.ModuleBlockNode;
import org.solvik.ast.statement.StatementNode;
import org.solvik.source.SourceSpan;

/**
 * A parsed Solvik program: the top-level items in source order, plus the EOF position metadata.
 *
 * <p>The language treats a physical file as a source container rather than a namespace, so a unit
 * holds: file-level {@code include} directives (replaced by the included file's items before
 * semantic analysis), zero or more {@code module Name { ... }} blocks, the default-module
 * declarations, and the executable top-level statements that form the implicit entry point
 * (docs/LANGUAGE_SPEC.md sections 6 and 20). Several blocks with one name — in one file or across
 * included files — contribute to the same module, so {@link #declarations()} flattens every
 * declaration of every module in source order and {@link #moduleNameOf(DeclarationNode)} reports the
 * module a declaration belongs to, with an empty result meaning the implicit default module.
 */
public final class CompilationUnitNode extends AstNode {

    private final List<AstNode> items;
    private final List<DeclarationNode> declarations;
    private final List<StatementNode> statements;
    private final List<ModuleBlockNode> modules;
    private final Map<DeclarationNode, String> moduleNames;
    private final boolean hasUnresolvedIncludes;

    public CompilationUnitNode(List<AstNode> items, SourceSpan span) {
        super(AstKind.COMPILATION_UNIT, span);
        this.items = List.copyOf(items);
        List<DeclarationNode> declarationList = new ArrayList<>();
        List<StatementNode> statementList = new ArrayList<>();
        List<ModuleBlockNode> moduleList = new ArrayList<>();
        Map<DeclarationNode, String> names = new IdentityHashMap<>();
        boolean unresolved = false;
        for (AstNode item : this.items) {
            Objects.requireNonNull(item, "item");
            if (item instanceof DeclarationNode declaration) {
                declarationList.add(declaration);
            } else if (item instanceof StatementNode statement) {
                statementList.add(statement);
            } else if (item instanceof IncludeDeclNode) {
                unresolved = true;
            } else if (item instanceof ModuleBlockNode module) {
                moduleList.add(module);
                for (DeclarationNode member : module.members()) {
                    declarationList.add(member);
                    names.put(member, module.name());
                }
            } else {
                throw new IllegalArgumentException("top-level node is neither a declaration nor a statement: " + item.getClass().getName());
            }
        }
        this.declarations = List.copyOf(declarationList);
        this.statements = List.copyOf(statementList);
        this.modules = List.copyOf(moduleList);
        this.moduleNames = names;
        this.hasUnresolvedIncludes = unresolved;
    }

    /** The top-level items in source order, including any unresolved include directives. */
    public List<AstNode> items() {
        return items;
    }

    /** Whether this unit still contains an {@code include} directive that must be resolved first. */
    public boolean hasUnresolvedIncludes() {
        return hasUnresolvedIncludes;
    }

    /** The named {@code module} blocks of the unit, in source order. */
    public List<ModuleBlockNode> modules() {
        return modules;
    }

    /** Every declaration of every module, in source order, plus the default module's declarations. */
    public List<DeclarationNode> declarations() {
        return declarations;
    }

    /** The module a declaration belongs to, or empty when it belongs to the implicit default module. */
    public Optional<String> moduleNameOf(DeclarationNode declaration) {
        return Optional.ofNullable(moduleNames.get(declaration));
    }

    /**
     * The executable top-level statements in source order. Non-empty exactly when the program has an
     * implicit {@code main}; each statement is a local of that implicit main, not a global. Named
     * modules are declaration-only, so every statement here belongs to the default module.
     */
    public List<StatementNode> statements() {
        return statements;
    }

    /** Whether the top-level statements form an implicit {@code main}. */
    public boolean hasImplicitMain() {
        return !statements.isEmpty();
    }

    @Override
    public List<AstNode> children() {
        return items;
    }
}
