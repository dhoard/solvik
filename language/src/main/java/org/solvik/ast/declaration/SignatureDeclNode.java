/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.declaration;

import java.util.List;
import org.solvik.ast.AstKind;
import org.solvik.source.SourceSpan;

/**
 * An interface abstract signature {@code func name(param: Type, ...): ReturnType;} terminated by
 * {@code ;} (docs/LANGUAGE_SPEC.md section 8). It declares a required member with no implementation:
 * every conforming class either implements it or inherits a default for it, otherwise static analysis
 * reports a missing implementation.
 */
public final class SignatureDeclNode extends CallableDeclNode {

    public SignatureDeclNode(String name, List<ParameterNode> parameters, TypeRefNode returnType, SourceSpan span) {
        this(name, List.of(), parameters, returnType, span);
    }

    public SignatureDeclNode(String name, List<TypeParameterNode> typeParameters, List<ParameterNode> parameters, TypeRefNode returnType, SourceSpan span) {
        super(AstKind.SIGNATURE_DECL, name, typeParameters, parameters, returnType, span);
    }

    @Override
    public boolean hasBody() {
        return false;
    }
}
