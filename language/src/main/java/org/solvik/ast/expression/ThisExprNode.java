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
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * The {@code this} expression (docs/LANGUAGE_SPEC.md section 7). It is valid only inside an
 * instance method or constructor; its static type is the enclosing class type.
 */
public final class ThisExprNode extends ExpressionNode {

    public ThisExprNode(SourceSpan span) {
        super(AstKind.THIS_EXPR, span);
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}
