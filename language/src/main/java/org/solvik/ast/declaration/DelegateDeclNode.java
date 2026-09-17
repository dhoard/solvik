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
package org.solvik.ast.declaration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.source.SourceSpan;

/**
 * A delegate property declaration {@code delegate val name: InterfaceType [= initializer]}
 * (docs/LANGUAGE_SPEC.md section 9). A delegate removes forwarding boilerplate: the compiler
 * synthesizes forwarding behavior for the interface members of its declared type that the class
 * does not implement itself.
 *
 * <p>A delegate is structurally an immutable, explicitly typed property, so the node carries the
 * same surface as a {@link PropertyDeclNode}: a required type reference and an optional declaration
 * initializer. It never carries {@code var}, and its declared type must be an interface; the
 * semantic layer rejects both violations, so the type reference is modelled as required here.
 */
public final class DelegateDeclNode extends AstNode {

    private final String name;
    private final TypeRefNode declaredType;
    private final ExpressionNode initializer;

    public DelegateDeclNode(String name, TypeRefNode declaredType, ExpressionNode initializer, SourceSpan span) {
        super(AstKind.DELEGATE_DECL, span);
        this.name = Objects.requireNonNull(name);
        this.declaredType = Objects.requireNonNull(declaredType);
        this.initializer = initializer;
    }

    public String name() {
        return name;
    }

    /** The written interface type reference; a delegate's type annotation is never inferred. */
    public TypeRefNode declaredType() {
        return declaredType;
    }

    /** The declaration initializer when present; otherwise the delegate is set in {@code init}. */
    public Optional<ExpressionNode> initializer() {
        return Optional.ofNullable(initializer);
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>();
        kids.add(declaredType);
        if (initializer != null) {
            kids.add(initializer);
        }
        return List.copyOf(kids);
    }
}
