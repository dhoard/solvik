/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.expression;

import java.util.List;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/** The {@code null} literal (docs/LANGUAGE_SPEC.md section 5), whose only type is {@code Null}. */
public final class NullLiteralNode extends ExpressionNode {

    public NullLiteralNode(SourceSpan span) {
        super(AstKind.NULL_LITERAL, span);
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}
