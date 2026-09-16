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
 * A {@code Long} literal, written with an {@code L}/{@code l} suffix (docs/LANGUAGE_SPEC.md
 * section 1). The lexeme includes the suffix; range checking against signed 64-bit is a static
 * diagnostic.
 */
public final class LongLiteralNode extends LiteralNode {

    public LongLiteralNode(String lexeme, SourceSpan span) {
        super(AstKind.LONG_LITERAL, lexeme, span);
    }
}
