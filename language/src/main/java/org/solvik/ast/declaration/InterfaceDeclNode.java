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
 * An {@code interface Name extends A, B { ... }} declaration (docs/LANGUAGE_SPEC.md section 8).
 * Interfaces define nominal contracts and may provide default method implementations; they contain
 * methods, not stored properties, so the grammar accepts only signatures and default methods in the
 * body.
 *
 * <p>Interface extension is multiple: {@code superInterfaces} is the complete {@code extends} list.
 * A {@code signature} is an abstract member terminated by {@code ;}; a {@code defaultMethod} is a
 * {@code func} with a body.
 */
public final class InterfaceDeclNode extends DeclarationNode {

    private final String name;
    private final List<TypeParameterNode> typeParameters;
    private final List<TypeRefNode> superInterfaces;
    private final List<SignatureDeclNode> signatures;
    private final List<FunctionDeclNode> defaultMethods;

    public InterfaceDeclNode(String name, List<TypeRefNode> superInterfaces, List<SignatureDeclNode> signatures, List<FunctionDeclNode> defaultMethods, SourceSpan span) {
        this(name, List.of(), superInterfaces, signatures, defaultMethods, span);
    }

    public InterfaceDeclNode(String name, List<TypeParameterNode> typeParameters, List<TypeRefNode> superInterfaces, List<SignatureDeclNode> signatures, List<FunctionDeclNode> defaultMethods, SourceSpan span) {
        super(AstKind.INTERFACE_DECL, span);
        this.name = Objects.requireNonNull(name);
        this.typeParameters = List.copyOf(typeParameters);
        this.superInterfaces = List.copyOf(superInterfaces);
        this.signatures = List.copyOf(signatures);
        this.defaultMethods = List.copyOf(defaultMethods);
    }

    public String name() {
        return name;
    }

    /** The declared type parameters of this generic interface, in source order. */
    public List<TypeParameterNode> typeParameters() {
        return typeParameters;
    }

    /** The written {@code extends} interface references, in source order. */
    public List<TypeRefNode> superInterfaces() {
        return superInterfaces;
    }

    /** The abstract member signatures declared directly by this interface. */
    public List<SignatureDeclNode> signatures() {
        return signatures;
    }

    /** The default method implementations declared directly by this interface. */
    public List<FunctionDeclNode> defaultMethods() {
        return defaultMethods;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>(typeParameters);
        kids.addAll(superInterfaces);
        kids.addAll(signatures);
        kids.addAll(defaultMethods);
        return List.copyOf(kids);
    }
}
