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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.statement.BlockNode;
import org.solvik.source.SourceSpan;

/**
 * An {@code if} expression (docs/LANGUAGE_SPEC.md section 21). The condition must be {@code Boolean}
 * exactly as for the statement form. An expression {@code if} must have an {@code else} path; each
 * normally completing branch must produce a tail result. The {@code else} path is either a value
 * block (an {@link BlockExprNode}) or a chained {@link IfExprNode}; an abrupt branch (one whose body
 * always transfers control) is permitted and contributes no result.
 */
public final class IfExprNode extends ExpressionNode {

    private final ExpressionNode condition;
    private final BlockNode thenBlock;
    private final ExpressionNode elseValue;

    public IfExprNode(ExpressionNode condition, BlockNode thenBlock, ExpressionNode elseValue, SourceSpan span) {
        super(AstKind.IF_EXPR, span);
        this.condition = Objects.requireNonNull(condition);
        this.thenBlock = Objects.requireNonNull(thenBlock);
        this.elseValue = elseValue;
    }

    public ExpressionNode condition() {
        return condition;
    }

    public BlockNode thenBlock() {
        return thenBlock;
    }

    /** The false path: a chained {@code if} expression or a value block; empty when none is written. */
    public Optional<ExpressionNode> elseValue() {
        return Optional.ofNullable(elseValue);
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>();
        kids.add(condition);
        kids.add(thenBlock);
        if (elseValue != null) {
            kids.add(elseValue);
        }
        return List.copyOf(kids);
    }
}
