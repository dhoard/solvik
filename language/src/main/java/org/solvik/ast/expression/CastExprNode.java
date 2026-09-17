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
import org.solvik.ast.declaration.TypeRefNode;
import org.solvik.source.SourceSpan;

/**
 * A checked cast {@code value as T} (docs/LANGUAGE_SPEC.md section 18). The cast expression has type
 * {@code T}; an unsuccessful cast raises a Solvik runtime type error rather than evaluating to
 * {@code null}. The operand and the written target type are children.
 */
public final class CastExprNode extends ExpressionNode {

    private final ExpressionNode operand;
    private final TypeRefNode typeRef;

    public CastExprNode(ExpressionNode operand, TypeRefNode typeRef, SourceSpan span) {
        super(AstKind.CAST_EXPR, span);
        this.operand = Objects.requireNonNull(operand);
        this.typeRef = Objects.requireNonNull(typeRef);
    }

    public ExpressionNode operand() {
        return operand;
    }

    public TypeRefNode typeRef() {
        return typeRef;
    }

    @Override
    public List<AstNode> children() {
        return List.of(operand, typeRef);
    }
}
