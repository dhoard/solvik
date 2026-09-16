/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast;

import java.util.List;
import java.util.Objects;
import org.solvik.ast.declaration.DeclarationNode;
import org.solvik.source.SourceSpan;

/**
 * A parsed Solvik source file: the top-level declarations (Phase 1: functions only) plus EOF
 * position metadata. This is the root of the syntax AST returned by the parser.
 */
public final class CompilationUnitNode extends AstNode {

    private final List<DeclarationNode> declarations;

    public CompilationUnitNode(List<DeclarationNode> declarations, SourceSpan span) {
        super(AstKind.COMPILATION_UNIT, span);
        this.declarations = List.copyOf(declarations);
    }

    public List<DeclarationNode> declarations() {
        return declarations;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<AstNode> children() {
        return List.copyOf((List) declarations);
    }
}
