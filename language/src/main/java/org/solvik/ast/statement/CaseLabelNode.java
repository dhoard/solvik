/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.statement;

import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * Base class of the two {@code switch} case label forms (docs/LANGUAGE_SPEC.md section 13): a
 * compile-time constant expression and a {@code regex <string literal>} pattern. A label is a syntax
 * node like any other, carrying a source span and ordered children but no resolved symbol or runtime
 * representation.
 */
public abstract class CaseLabelNode extends AstNode {

    protected CaseLabelNode(AstKind kind, SourceSpan span) {
        super(kind, span);
    }
}
