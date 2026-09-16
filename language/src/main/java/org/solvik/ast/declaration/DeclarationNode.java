/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.declaration;

import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/** Base class for top-level declarations. Phase 1 supports only function declarations. */
public abstract class DeclarationNode extends AstNode {

    protected DeclarationNode(AstKind kind, SourceSpan span) {
        super(kind, span);
    }
}
