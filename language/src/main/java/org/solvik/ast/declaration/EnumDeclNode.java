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
import org.solvik.source.SourceSpan;

/**
 * An {@code enum Name<T, ...> { Variant(Type, ...) ... }} declaration (docs/LANGUAGE_SPEC.md
 * section 12). An enum is a closed nominal type whose complete variant set is known when the file
 * is compiled, which is what makes it usable for exhaustive {@code match} in a later phase.
 *
 * <p>The declaration has no superclass and no interfaces: enums are closed, so a class may not
 * extend one, and an enum may not extend or implement anything. Members other than variants are
 * rejected by the grammar.
 */
public final class EnumDeclNode extends DeclarationNode {

    private final String name;
    private final List<TypeParameterNode> typeParameters;
    private final List<EnumVariantNode> variants;

    public EnumDeclNode(String name, List<EnumVariantNode> variants, SourceSpan span) {
        this(name, List.of(), variants, span);
    }

    public EnumDeclNode(String name, List<TypeParameterNode> typeParameters, List<EnumVariantNode> variants, SourceSpan span) {
        super(AstKind.ENUM_DECL, span);
        this.name = Objects.requireNonNull(name);
        this.typeParameters = List.copyOf(typeParameters);
        this.variants = List.copyOf(variants);
    }

    public String name() {
        return name;
    }

    /** The declared type parameters of this generic enum, in source order. */
    public List<TypeParameterNode> typeParameters() {
        return typeParameters;
    }

    /** The variants in source order, which is their construction and closed-set order. */
    public List<EnumVariantNode> variants() {
        return variants;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>(typeParameters);
        kids.addAll(variants);
        return List.copyOf(kids);
    }
}
