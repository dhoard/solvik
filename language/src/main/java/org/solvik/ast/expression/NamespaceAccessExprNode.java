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
 * A module-qualified name {@code prefix::name} (docs/LANGUAGE_SPEC.md section 20). The receiver is
 * the namespace expression: a plain name reference for {@code math::add}, a nested namespace access
 * for {@code math::Result::Ok}, or a member access for a qualified enum variant such as
 * {@code math::Result.Ok}. The {@code ::} separator is distinct from the {@code .} member-access
 * operator, so a qualified reference is unambiguous.
 *
 * <p>A namespace access is never a value: the semantic layer resolves it to a module declaration or
 * rejects it. It appears as the callee of a qualified call, as the receiver of a qualified variant,
 * or on the left of a qualified type reference.
 */
public final class NamespaceAccessExprNode extends ExpressionNode {

    private final ExpressionNode receiver;
    private final String memberName;

    public NamespaceAccessExprNode(ExpressionNode receiver, String memberName, SourceSpan span) {
        super(AstKind.NAMESPACE_ACCESS_EXPR, span);
        this.receiver = Objects.requireNonNull(receiver);
        this.memberName = Objects.requireNonNull(memberName);
    }

    public ExpressionNode receiver() {
        return receiver;
    }

    public String memberName() {
        return memberName;
    }

    @Override
    public List<AstNode> children() {
        return List.of(receiver);
    }
}
