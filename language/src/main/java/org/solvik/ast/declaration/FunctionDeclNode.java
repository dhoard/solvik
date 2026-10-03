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
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.statement.BlockNode;
import org.solvik.source.SourceSpan;

/**
 * A {@code func name(param: Type, ...): ReturnType { ... }} declaration with a body. The same node
 * represents a top-level function, a class instance method, and an interface default method
 * (docs/LANGUAGE_SPEC.md sections 6 and 8); an interface <em>abstract signature</em>, which has no
 * body, is the distinct {@link SignatureDeclNode}, and a constructor is
 * {@link ConstructorDeclNode}.
 *
 * <p>{@code mutable} and {@code override} are class method modifiers (docs/LANGUAGE_SPEC.md section 7).
 * They are always {@code false} for a top-level function and for an interface member: an interface
 * method is inherited by every implementor and needs no modifier, and a top-level function is never
 * overridable. The semantic layer rejects a modifier written where the grammar permits none.
 */
public final class FunctionDeclNode extends CallableDeclNode {

    private final boolean mutable;
    private final boolean override;
    private final boolean isStatic;
    private final BlockNode body;

    public FunctionDeclNode(boolean mutable, boolean override, String name, List<ParameterNode> parameters, TypeRef returnType, BlockNode body, SourceSpan span) {
        this(mutable, override, name, List.of(), parameters, returnType, body, span);
    }

    public FunctionDeclNode(boolean mutable, boolean override, String name, List<TypeParameterNode> typeParameters, List<ParameterNode> parameters, TypeRef returnType, BlockNode body, SourceSpan span) {
        this(mutable, override, false, name, typeParameters, parameters, returnType, body, span);
    }

    /**
     * The full form. {@code isStatic} marks a class-level static member (docs/LANGUAGE_SPEC.md
     * section 7), which has no {@code this} receiver and is reached through the class name.
     */
    public FunctionDeclNode(boolean mutable, boolean override, boolean isStatic, String name, List<TypeParameterNode> typeParameters, List<ParameterNode> parameters, TypeRef returnType, BlockNode body, SourceSpan span) {
        super(AstKind.FUNCTION_DECL, name, typeParameters, parameters, returnType, span);
        this.mutable = mutable;
        this.override = override;
        this.isStatic = isStatic;
        this.body = Objects.requireNonNull(body);
    }

    /** Whether the method was declared {@code mutable} and may therefore be overridden. */
    public boolean isMutable() {
        return mutable;
    }

    /** Whether the method was declared {@code static}, which makes it a class-level member. */
    public boolean isStatic() {
        return isStatic;
    }

    /** Whether the method was declared {@code override}, which is mandatory for an override. */
    public boolean isOverride() {
        return override;
    }

    public BlockNode body() {
        return body;
    }

    @Override
    public boolean hasBody() {
        return true;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>(super.children());
        kids.add(body);
        return List.copyOf(kids);
    }
}
