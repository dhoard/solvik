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
package org.solvik.ast.statement;

import java.util.List;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.declaration.TypeRef;
import org.solvik.source.SourceSpan;

/**
 * A {@code try} statement (error-handling phases). It groups an operation with zero or more catch
 * clauses and at most one finally clause. Each catch clause names a binding, an exception type, and
 * a body block; the first compatible handler runs. The construct must have at least one catch or
 * finally clause, which semantic analysis enforces.
 */
public final class TryStmtNode extends StatementNode {

    /** One {@code catch (name: Type) block} clause. It is parsed data, not a traversed AST node: its
     * body and exception type are reachable directly through {@link #children()} and the accessors. */
    public record CatchClause(String bindingName, TypeRef exceptionType, BlockNode body) {
    }

    private final BlockNode tryBlock;
    private final List<CatchClause> catchClauses;
    private final BlockNode finallyBlock;

    public TryStmtNode(BlockNode tryBlock, List<CatchClause> catchClauses, BlockNode finallyBlock, SourceSpan span) {
        super(AstKind.TRY_STMT, span);
        this.tryBlock = tryBlock;
        this.catchClauses = List.copyOf(catchClauses);
        this.finallyBlock = finallyBlock;
    }

    public BlockNode tryBlock() {
        return tryBlock;
    }

    public List<CatchClause> catchClauses() {
        return catchClauses;
    }

    public BlockNode finallyBlock() {
        return finallyBlock;
    }

    /** The exception type written for each catch clause, in source order (for duplicate detection). */
    public List<TypeRef> catchTypeReferences() {
        return catchClauses.stream().map(CatchClause::exceptionType).toList();
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> result = new java.util.ArrayList<>();
        result.add(tryBlock);
        for (CatchClause clause : catchClauses) {
            result.add(clause.body());
        }
        if (finallyBlock != null) {
            result.add(finallyBlock);
        }
        return result;
    }
}
