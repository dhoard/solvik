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
 * A decimal floating-point literal with an optional exponent (docs/LANGUAGE_SPEC.md section 1).
 * A trailing {@code f}/{@code F} suffix selects {@code Float}; otherwise the literal has type
 * {@code Double}.
 */
public final class FloatingLiteralNode extends LiteralNode {

    public FloatingLiteralNode(String lexeme, SourceSpan span) {
        super(AstKind.FLOATING_LITERAL, lexeme, span);
    }

    /** Whether the literal carries the {@code f}/{@code F} suffix that selects {@code Float}. */
    public boolean isFloat() {
        String text = lexeme();
        char last = text.charAt(text.length() - 1);
        return last == 'f' || last == 'F';
    }

    /** The literal text without its optional type suffix, suitable for parsing. */
    public String numericText() {
        return isFloat() ? lexeme().substring(0, lexeme().length() - 1) : lexeme();
    }
}
