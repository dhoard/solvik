/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.expression;

import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/** Base class for value-producing expressions. */
public abstract class ExpressionNode extends AstNode {

    protected ExpressionNode(AstKind kind, SourceSpan span) {
        super(kind, span);
    }
}
