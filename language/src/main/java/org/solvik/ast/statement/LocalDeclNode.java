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
import java.util.Optional;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.declaration.TypeRefNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.source.SourceSpan;

/**
 * A {@code val} or {@code var} local declaration with an initializer. The type annotation is
 * syntactically optional; inference rules are a Phase 4 concern.
 */
public final class LocalDeclNode extends StatementNode {

    private final BindingKind bindingKind;
    private final String name;
    private final TypeRefNode declaredType;
    private final ExpressionNode initializer;

    public LocalDeclNode(BindingKind bindingKind, String name, TypeRefNode declaredType, ExpressionNode initializer, SourceSpan span) {
        super(AstKind.LOCAL_DECL, span);
        this.bindingKind = Objects.requireNonNull(bindingKind);
        this.name = Objects.requireNonNull(name);
        this.declaredType = declaredType;
        this.initializer = Objects.requireNonNull(initializer);
    }

    public BindingKind bindingKind() {
        return bindingKind;
    }

    public String name() {
        return name;
    }

    /** The written type annotation, or empty when only the initializer spelling exists. */
    public Optional<TypeRefNode> declaredType() {
        return Optional.ofNullable(declaredType);
    }

    public ExpressionNode initializer() {
        return initializer;
    }

    @Override
    public List<AstNode> children() {
        if (declaredType == null) {
            return List.of(initializer);
        }
        return List.of(declaredType, initializer);
    }
}
