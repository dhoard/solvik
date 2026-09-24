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
import org.solvik.ast.statement.BindingKind;
import org.solvik.source.SourceSpan;

/**
 * A class property declaration {@code val name: Type [= initializer]} or
 * {@code var name: Type [= initializer]} (docs/LANGUAGE_SPEC.md section 7). Unlike a local
 * declaration the initializer is optional; a property without one must be assigned on every
 * successful constructor path.
 */
public final class PropertyDeclNode extends AstNode {

    private final BindingKind bindingKind;
    private final String name;
    private final TypeRefNode declaredType;
    private final ExpressionNode initializer;
    private final boolean isStatic;

    public PropertyDeclNode(BindingKind bindingKind, String name, TypeRefNode declaredType, ExpressionNode initializer, SourceSpan span) {
        this(bindingKind, name, declaredType, initializer, false, span);
    }

    /** The full form. {@code isStatic} marks a class-level property (docs/LANGUAGE_SPEC.md section 7). */
    public PropertyDeclNode(BindingKind bindingKind, String name, TypeRefNode declaredType, ExpressionNode initializer, boolean isStatic, SourceSpan span) {
        super(AstKind.PROPERTY_DECL, span);
        this.bindingKind = Objects.requireNonNull(bindingKind);
        this.name = Objects.requireNonNull(name);
        this.declaredType = declaredType;
        this.initializer = initializer;
        this.isStatic = isStatic;
    }

    /** Whether this is a class-level {@code static} property rather than an instance property. */
    public boolean isStatic() {
        return isStatic;
    }

    public BindingKind bindingKind() {
        return bindingKind;
    }

    public String name() {
        return name;
    }

    /** The written type reference when the property has an explicit annotation. */
    public Optional<TypeRefNode> declaredType() {
        return Optional.ofNullable(declaredType);
    }

    /** The declaration initializer when present. */
    public Optional<ExpressionNode> initializer() {
        return Optional.ofNullable(initializer);
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>();
        if (declaredType != null) {
            kids.add(declaredType);
        }
        if (initializer != null) {
            kids.add(initializer);
        }
        return List.copyOf(kids);
    }
}
