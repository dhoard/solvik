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
 * The postfix propagation operator {@code expression?} (error-handling phases). Its single operand is
 * required by static analysis to have a {@code Result<T, E>} type: the node produces {@code T} when
 * the evaluated value is {@code Ok}, and otherwise propagates the carried {@code Err} through the
 * enclosing {@code Result}-returning boundary. The operand is the only child and is evaluated exactly
 * once; the whole postfix sequence keeps the span of its leftmost primary so diagnostics point at the
 * original expression rather than the trailing {@code ?}.
 */
public final class PropagationExprNode extends ExpressionNode {

    private final ExpressionNode operand;

    public PropagationExprNode(ExpressionNode operand, SourceSpan span) {
        super(AstKind.PROPAGATION_EXPR, span);
        this.operand = Objects.requireNonNull(operand);
    }

    /** The propagated operand, whose static type is {@code Result<T, E>}. */
    public ExpressionNode operand() {
        return operand;
    }

    @Override
    public List<AstNode> children() {
        return List.of(operand);
    }
}
