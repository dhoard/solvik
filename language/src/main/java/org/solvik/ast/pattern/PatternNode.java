/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.pattern;

import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * Base class of the initial {@code match} pattern forms (docs/LANGUAGE_SPEC.md section 12): an enum
 * variant pattern, a sealed-subtype binding pattern of the form {@code name: Type}, and the
 * wildcard {@code _}. A pattern is a syntax node exactly like any other AST node, so it carries a
 * source span and ordered children but no resolved symbol or runtime representation.
 */
public abstract class PatternNode extends AstNode {

    protected PatternNode(AstKind kind, SourceSpan span) {
        super(kind, span);
    }
}
