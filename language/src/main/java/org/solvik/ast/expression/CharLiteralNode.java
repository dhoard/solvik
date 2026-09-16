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
 * A character literal, written with single quotes (docs/LANGUAGE_SPEC.md section 1). The lexeme
 * includes the quotes; the semantic pass validates that its content is exactly one Unicode scalar
 * value or one supported escape and computes the character value.
 */
public final class CharLiteralNode extends LiteralNode {

    public CharLiteralNode(String lexeme, SourceSpan span) {
        super(AstKind.CHAR_LITERAL, lexeme, span);
    }
}
