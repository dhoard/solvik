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
package org.solvik.ast.expression;

import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.statement.BlockNode;
import org.solvik.source.SourceSpan;

/**
 * A brace-delimited block expression (docs/LANGUAGE_SPEC.md section 21). It introduces one lexical
 * scope, executes its statements in source order, and produces the value of its tail expression.
 * The body is a {@link BlockNode} whose {@link BlockNode#tail()} carries the value; a value-required
 * body with no reachable tail result is rejected during semantic analysis and never lowered.
 */
public final class BlockExprNode extends ExpressionNode {

    private final BlockNode body;

    public BlockExprNode(BlockNode body, SourceSpan span) {
        super(AstKind.BLOCK_EXPR, span);
        this.body = Objects.requireNonNull(body);
    }

    public BlockNode body() {
        return body;
    }

    @Override
    public List<AstNode> children() {
        return List.of(body);
    }
}
