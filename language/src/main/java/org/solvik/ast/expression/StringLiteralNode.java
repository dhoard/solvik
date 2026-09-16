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
 * A normal (non-raw) string literal. {@link #lexeme()} includes the surrounding quotes; escape
 * validation and unescaping belong to lexical hardening in later phases.
 */
public final class StringLiteralNode extends LiteralNode {

    public StringLiteralNode(String lexeme, SourceSpan span) {
        super(AstKind.STRING_LITERAL, lexeme, span);
    }
}
