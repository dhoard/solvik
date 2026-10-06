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
import org.solvik.source.SourceSpan;

/**
 * A {@code class} declaration with at most one leading modifier — {@code mutable} or {@code abstract}
 * — an optional single {@code extends} superclass, and an optional {@code implements} interface list
 * (docs/LANGUAGE_SPEC.md sections 7 and 12). Classes are final by default: only a {@code mutable
 * class} or an {@code abstract class} may be extended. The grammar's {@code (ABSTRACT | MUTABLE)?}
 * alternation admits no combination of the two, because {@code abstract} already grants extension and
 * leaves {@code mutable} no bit to flip. The grammar permits at most one {@code extends} clause, so
 * multiple inheritance is a parse error rather than a semantic one, while {@code implements} accepts
 * several interfaces.
 *
 * <p>The body is kept as one source-ordered member list, because a class may interleave properties,
 * delegates, its constructor, and methods freely. {@link #properties()}, {@link #delegates()},
 * {@link #constructors()}, and {@link #methods()} are the kind-filtered views over that list and
 * preserve source order within each kind.
 *
 * <p>The grammar permits more than one constructor so the semantic layer can report
 * {@code SOLV-SEM-007}; a valid class keeps exactly one.
 */
public final class ClassDeclNode extends DeclarationNode {

    private final boolean abstractClass;
    private final boolean mutable;
    private final String name;
    private final List<TypeParameterNode> typeParameters;
    private final TypeRef superClass;
    private final List<TypeRef> interfaces;
    private final List<AstNode> members;

    public ClassDeclNode(boolean mutable, String name, TypeRef superClass, List<TypeRef> interfaces, List<AstNode> members, SourceSpan span) {
        this(false, mutable, name, List.of(), superClass, interfaces, members, span);
    }

    public ClassDeclNode(boolean mutable, String name, List<TypeParameterNode> typeParameters, TypeRef superClass, List<TypeRef> interfaces, List<AstNode> members, SourceSpan span) {
        this(false, mutable, name, typeParameters, superClass, interfaces, members, span);
    }

    public ClassDeclNode(boolean abstractClass, boolean mutable, String name, List<TypeParameterNode> typeParameters, TypeRef superClass, List<TypeRef> interfaces, List<AstNode> members, SourceSpan span) {
        super(AstKind.CLASS_DECL, span);
        this.abstractClass = abstractClass;
        this.mutable = mutable;
        this.name = Objects.requireNonNull(name);
        this.typeParameters = List.copyOf(typeParameters);
        this.superClass = superClass;
        this.interfaces = List.copyOf(interfaces);
        this.members = List.copyOf(members);
    }

    /**
     * Whether the class was declared {@code abstract} (docs/LANGUAGE_SPEC.md section 12). An abstract
     * class cannot be constructed and exists to be extended, from any file. {@code abstract} is a Java
     * keyword, so the accessor carries the name rather than the field.
     */
    public boolean isAbstract() {
        return abstractClass;
    }

    /** The declared type parameters of this generic class, in source order. */
    public List<TypeParameterNode> typeParameters() {
        return typeParameters;
    }

    /** Whether the class was declared {@code mutable} and may therefore be extended. */
    public boolean isMutable() {
        return mutable;
    }

    public String name() {
        return name;
    }

    /** The written {@code extends} superclass reference, when the class has one. */
    public Optional<TypeRef> superClass() {
        return Optional.ofNullable(superClass);
    }

    /** The written {@code implements} interface references, in source order. */
    public List<TypeRef> interfaces() {
        return interfaces;
    }

    /** Every class member in source order: properties, delegates, constructors, methods, and static members. */
    public List<AstNode> members() {
        return members;
    }

    /**
     * The instance {@code var} and {@code var mutable} property declarations, in source order. Static
     * properties are excluded (docs/LANGUAGE_SPEC.md section 7): they are class-level storage, so they
     * must not join the per-instance property list that drives object layout, constructor
     * initialization, and member access.
     */
    public List<PropertyDeclNode> properties() {
        List<PropertyDeclNode> found = new ArrayList<>();
        for (PropertyDeclNode property : membersOfKind(PropertyDeclNode.class)) {
            if (!property.isStatic()) {
                found.add(property);
            }
        }
        return List.copyOf(found);
    }

    /** The {@code static var} and {@code static var mutable} declarations, in source order. */
    public List<PropertyDeclNode> staticProperties() {
        List<PropertyDeclNode> found = new ArrayList<>();
        for (PropertyDeclNode property : membersOfKind(PropertyDeclNode.class)) {
            if (property.isStatic()) {
                found.add(property);
            }
        }
        return List.copyOf(found);
    }

    /** The {@code delegate var} declarations, in source order (docs/LANGUAGE_SPEC.md section 9). */
    public List<DelegateDeclNode> delegates() {
        return membersOfKind(DelegateDeclNode.class);
    }

    /** The constructor declarations, in source order; a valid class has at most one. */
    public List<ConstructorDeclNode> constructors() {
        return membersOfKind(ConstructorDeclNode.class);
    }

    /** The single constructor declaration when exactly one is written. */
    public Optional<ConstructorDeclNode> constructor() {
        List<ConstructorDeclNode> constructors = constructors();
        return constructors.isEmpty() ? Optional.empty() : Optional.of(constructors.get(0));
    }

    /**
     * The instance method declarations, in source order. Static methods are excluded: they are not
     * inherited, not overridable, and never enter a virtual method table.
     */
    public List<FunctionDeclNode> methods() {
        List<FunctionDeclNode> found = new ArrayList<>();
        for (FunctionDeclNode method : membersOfKind(FunctionDeclNode.class)) {
            if (!method.isStatic()) {
                found.add(method);
            }
        }
        return List.copyOf(found);
    }

    /** The {@code static func} declarations, in source order. */
    public List<FunctionDeclNode> staticMethods() {
        List<FunctionDeclNode> found = new ArrayList<>();
        for (FunctionDeclNode method : membersOfKind(FunctionDeclNode.class)) {
            if (method.isStatic()) {
                found.add(method);
            }
        }
        return List.copyOf(found);
    }

    /** The class initializer blocks, in source order; a valid class declares at most one. */
    public List<StaticBlockNode> staticBlocks() {
        return membersOfKind(StaticBlockNode.class);
    }

    /** The class initializer block when one is written. */
    public Optional<StaticBlockNode> staticBlock() {
        List<StaticBlockNode> blocks = staticBlocks();
        return blocks.isEmpty() ? Optional.empty() : Optional.of(blocks.get(0));
    }

    private <T> List<T> membersOfKind(Class<T> memberType) {
        List<T> found = new ArrayList<>();
        for (AstNode member : members) {
            if (memberType.isInstance(member)) {
                found.add(memberType.cast(member));
            }
        }
        return List.copyOf(found);
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>(typeParameters);
        if (superClass != null) {
            kids.add(superClass);
        }
        kids.addAll(interfaces);
        kids.addAll(members);
        return List.copyOf(kids);
    }
}
