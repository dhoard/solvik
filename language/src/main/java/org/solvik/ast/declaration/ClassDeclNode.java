/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
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
 * A {@code class} declaration with an optional {@code open} modifier, an optional single {@code
 * extends} superclass, and an optional {@code implements} interface list
 * (docs/LANGUAGE_SPEC.md sections 7 and 8). Classes are final by default: only an {@code open class}
 * may be extended. The grammar permits at most one {@code extends} clause, so multiple inheritance
 * is a parse error rather than a semantic one, while {@code implements} accepts several interfaces.
 *
 * <p>The grammar permits more than one {@code init} so the semantic layer can report
 * {@code SOLV-SEM-007}; a valid class keeps exactly one.
 */
public final class ClassDeclNode extends DeclarationNode {

    private final boolean open;
    private final String name;
    private final TypeRefNode superClass;
    private final List<TypeRefNode> interfaces;
    private final List<PropertyDeclNode> properties;
    private final List<InitDeclNode> initializers;
    private final List<FunctionDeclNode> methods;

    public ClassDeclNode(boolean open, String name, TypeRefNode superClass, List<TypeRefNode> interfaces, List<PropertyDeclNode> properties, List<InitDeclNode> initializers, List<FunctionDeclNode> methods, SourceSpan span) {
        super(AstKind.CLASS_DECL, span);
        this.open = open;
        this.name = Objects.requireNonNull(name);
        this.superClass = superClass;
        this.interfaces = List.copyOf(interfaces);
        this.properties = List.copyOf(properties);
        this.initializers = List.copyOf(initializers);
        this.methods = List.copyOf(methods);
    }

    /** Whether the class was declared {@code open} and may therefore be extended. */
    public boolean isOpen() {
        return open;
    }

    public String name() {
        return name;
    }

    /** The written {@code extends} superclass reference, when the class has one. */
    public Optional<TypeRefNode> superClass() {
        return Optional.ofNullable(superClass);
    }

    /** The written {@code implements} interface references, in source order. */
    public List<TypeRefNode> interfaces() {
        return interfaces;
    }

    public List<PropertyDeclNode> properties() {
        return properties;
    }

    public List<InitDeclNode> initializers() {
        return initializers;
    }

    /** The single {@code init} declaration when exactly one is written. */
    public Optional<InitDeclNode> initializer() {
        return initializers.isEmpty() ? Optional.empty() : Optional.of(initializers.get(0));
    }

    public List<FunctionDeclNode> methods() {
        return methods;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>();
        if (superClass != null) {
            kids.add(superClass);
        }
        kids.addAll(interfaces);
        kids.addAll(properties);
        kids.addAll(initializers);
        kids.addAll(methods);
        return List.copyOf(kids);
    }
}
