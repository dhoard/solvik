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
import org.solvik.source.SourceSpan;

/**
 * Shared base of every declaration that names a callable contract: a function, a method, a default
 * method, or an interface abstract signature (docs/LANGUAGE_SPEC.md sections 6 and 8). It carries the
 * parts interface conformance compares — name, parameter types, and return type — so the semantic
 * layer can treat an implemented method and a required signature uniformly.
 *
 * <p>Whether the declaration supplies an implementation is the {@link #hasBody()} distinction: an
 * abstract signature requires an implementation elsewhere, while a function, method, or default
 * method provides one.
 */
public abstract class CallableDeclNode extends DeclarationNode {

    private final String name;
    private final List<TypeParameterNode> typeParameters;
    private final List<ParameterNode> parameters;
    private final TypeRefNode returnType;

    protected CallableDeclNode(AstKind kind, String name, List<TypeParameterNode> typeParameters, List<ParameterNode> parameters, TypeRefNode returnType, SourceSpan span) {
        super(kind, span);
        this.name = Objects.requireNonNull(name);
        this.typeParameters = List.copyOf(typeParameters);
        this.parameters = List.copyOf(parameters);
        this.returnType = Objects.requireNonNull(returnType);
    }

    public final String name() {
        return name;
    }

    /** The declared type parameters of this generic callable, in source order. */
    public final List<TypeParameterNode> typeParameters() {
        return typeParameters;
    }

    public final List<ParameterNode> parameters() {
        return parameters;
    }

    public final TypeRefNode returnType() {
        return returnType;
    }

    /** Whether this declaration supplies an implementation body. */
    public abstract boolean hasBody();

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>(typeParameters);
        kids.addAll(parameters);
        kids.add(returnType);
        return List.copyOf(kids);
    }
}
