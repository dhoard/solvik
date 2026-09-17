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
import java.util.List;
import java.util.Objects;
import org.solvik.ast.declaration.DeclarationNode;
import org.solvik.ast.statement.StatementNode;
import org.solvik.source.SourceSpan;

/**
 * A parsed Solvik source file: the top-level declarations and the executable top-level statements,
 * in source order, plus the EOF position metadata. The statements form the implicit
 * {@code func main(): Unit} body (docs/LANGUAGE_SPEC.md section 6); a file that mixes declarations
 * and statements freely keeps both kinds in one ordered list so AST traversal still follows source
 * order. This is the root of the syntax AST returned by the parser.
 */
public final class CompilationUnitNode extends AstNode {

    private final List<AstNode> items;
    private final List<DeclarationNode> declarations;
    private final List<StatementNode> statements;

    public CompilationUnitNode(List<AstNode> items, SourceSpan span) {
        super(AstKind.COMPILATION_UNIT, span);
        this.items = List.copyOf(items);
        List<DeclarationNode> declarationList = new ArrayList<>();
        List<StatementNode> statementList = new ArrayList<>();
        for (AstNode item : this.items) {
            Objects.requireNonNull(item, "item");
            if (item instanceof DeclarationNode declaration) {
                declarationList.add(declaration);
            } else if (item instanceof StatementNode statement) {
                statementList.add(statement);
            } else {
                throw new IllegalArgumentException("top-level node is neither a declaration nor a statement: " + item.getClass().getName());
            }
        }
        this.declarations = List.copyOf(declarationList);
        this.statements = List.copyOf(statementList);
    }

    public List<DeclarationNode> declarations() {
        return declarations;
    }

    /**
     * The executable top-level statements in source order. Non-empty exactly when the file has an
     * implicit {@code main}; each statement is a local of that implicit main, not a global.
     */
    public List<StatementNode> statements() {
        return statements;
    }

    /** Whether the file's top-level statements form an implicit {@code main}. */
    public boolean hasImplicitMain() {
        return !statements.isEmpty();
    }

    @Override
    public List<AstNode> children() {
        return items;
    }
}
