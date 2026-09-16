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
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.statement.BlockNode;
import org.solvik.source.SourceSpan;

/**
 * A {@code fun name(param: Type, ...): ReturnType { ... }} declaration. The same node represents a
 * top-level function, a class instance method, and (through {@link InitDeclNode}) no constructor.
 *
 * <p>{@code open} and {@code override} are method modifiers (docs/LANGUAGE_SPEC.md section 7). They
 * are always {@code false} for a top-level function, which is never overridable; the semantic layer
 * rejects a modifier written anywhere the grammar should not have permitted it.
 */
public final class FunctionDeclNode extends DeclarationNode {

    private final boolean open;
    private final boolean override;
    private final String name;
    private final List<ParameterNode> parameters;
    private final TypeRefNode returnType;
    private final BlockNode body;

    public FunctionDeclNode(boolean open, boolean override, String name, List<ParameterNode> parameters, TypeRefNode returnType, BlockNode body, SourceSpan span) {
        super(AstKind.FUNCTION_DECL, span);
        this.open = open;
        this.override = override;
        this.name = Objects.requireNonNull(name);
        this.parameters = List.copyOf(parameters);
        this.returnType = Objects.requireNonNull(returnType);
        this.body = Objects.requireNonNull(body);
    }

    /** Whether the method was declared {@code open} and may therefore be overridden. */
    public boolean isOpen() {
        return open;
    }

    /** Whether the method was declared {@code override}, which is mandatory for an override. */
    public boolean isOverride() {
        return override;
    }

    public String name() {
        return name;
    }

    public List<ParameterNode> parameters() {
        return parameters;
    }

    public TypeRefNode returnType() {
        return returnType;
    }

    public BlockNode body() {
        return body;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>(parameters);
        kids.add(returnType);
        kids.add(body);
        return List.copyOf(kids);
    }
}
