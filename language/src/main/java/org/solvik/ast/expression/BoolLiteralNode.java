/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.expression;

import org.solvik.ast.AstKind;
import org.solvik.source.SourceSpan;

/** A {@code true} or {@code false} literal. */
public final class BoolLiteralNode extends LiteralNode {

    public BoolLiteralNode(String lexeme, SourceSpan span) {
        super(AstKind.BOOL_LITERAL, lexeme, span);
    }

    public boolean value() {
        return "true".equals(lexeme());
    }
}
