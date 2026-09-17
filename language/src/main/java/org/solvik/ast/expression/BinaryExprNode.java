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
import org.solvik.source.SourceSpan;

/**
 * A left-associative binary expression such as {@code a + b}. The grammar encodes precedence
 * directly (multiplicative binds tighter than additive) and never produces chains requiring
 * runtime precedence resolution.
 */
public final class BinaryExprNode extends ExpressionNode {

    private final BinaryOperator operator;
    private final ExpressionNode left;
    private final ExpressionNode right;

    public BinaryExprNode(BinaryOperator operator, ExpressionNode left, ExpressionNode right, SourceSpan span) {
        super(AstKind.BINARY_EXPR, span);
        this.operator = Objects.requireNonNull(operator);
        this.left = Objects.requireNonNull(left);
        this.right = Objects.requireNonNull(right);
    }

    public BinaryOperator operator() {
        return operator;
    }

    public ExpressionNode left() {
        return left;
    }

    public ExpressionNode right() {
        return right;
    }

    @Override
    public List<AstNode> children() {
        return List.of(left, right);
    }
}
