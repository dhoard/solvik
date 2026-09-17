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
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.source.SourceSpan;

/**
 * A constant {@code switch} case label (docs/LANGUAGE_SPEC.md section 13), such as
 * {@code case 1:} or {@code case "a", "b":}. The static semantic pass, not the grammar, decides
 * whether the written expression is a compile-time constant and assignable to the switched value's
 * type.
 */
public final class ConstantCaseLabelNode extends CaseLabelNode {

    private final ExpressionNode expression;

    public ConstantCaseLabelNode(ExpressionNode expression, SourceSpan span) {
        super(AstKind.CASE_LABEL, span);
        this.expression = Objects.requireNonNull(expression);
    }

    public ExpressionNode expression() {
        return expression;
    }

    @Override
    public List<AstNode> children() {
        return List.of(expression);
    }
}
