/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.pattern;

import java.util.List;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * The wildcard pattern {@code _} (docs/LANGUAGE_SPEC.md section 12). It matches every value and
 * binds no name, so its presence makes a {@code match} exhaustive regardless of the closed variant
 * set.
 */
public final class WildcardPatternNode extends PatternNode {

    public WildcardPatternNode(SourceSpan span) {
        super(AstKind.WILDCARD_PATTERN, span);
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}
