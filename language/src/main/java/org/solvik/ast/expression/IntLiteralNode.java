/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.expression;

import org.solvik.ast.AstKind;
import org.solvik.source.SourceSpan;

/**
 * A decimal integer literal. Phase 1 records the lexeme only; range checking against {@code Int}
 * (signed 32-bit) is a Phase 4 static-analysis diagnostic.
 */
public final class IntLiteralNode extends LiteralNode {

    public IntLiteralNode(String lexeme, SourceSpan span) {
        super(AstKind.INT_LITERAL, lexeme, span);
    }
}
