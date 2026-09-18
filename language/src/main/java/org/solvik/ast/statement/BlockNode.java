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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.source.SourceSpan;

/**
 * A brace-delimited block. Each block introduces a new lexical scope. A block used in statement
 * position has no tail result; a block used as a value-required body (a block expression, an
 * {@code if}-expression branch, or a {@code switch}-expression case body) may carry one optional
 * tail expression whose value and type are the body result (docs/LANGUAGE_SPEC.md section 21).
 */
public final class BlockNode extends StatementNode {

    private final List<StatementNode> statements;
    private final ExpressionNode tail;

    /** A statement block with no tail result. */
    public BlockNode(List<StatementNode> statements, SourceSpan span) {
        this(statements, null, span);
    }

    /**
     * A block whose statements are followed by an optional tail expression. A value-required body
     * that carries no tail expression is invalid; the semantic layer reports it.
     */
    public BlockNode(List<StatementNode> statements, ExpressionNode tail, SourceSpan span) {
        super(AstKind.BLOCK, span);
        this.statements = List.copyOf(statements);
        this.tail = tail;
    }

    public List<StatementNode> statements() {
        return statements;
    }

    /** The tail expression of a value-required block, when it reaches the closing brace with one. */
    public Optional<ExpressionNode> tail() {
        return Optional.ofNullable(tail);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<AstNode> children() {
        List<AstNode> kids = new ArrayList<>((List) statements);
        if (tail != null) {
            kids.add(tail);
        }
        return List.copyOf(kids);
    }
}
