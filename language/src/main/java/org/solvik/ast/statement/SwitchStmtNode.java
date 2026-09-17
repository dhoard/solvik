/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.source.SourceSpan;

/**
 * A non-fallthrough {@code switch (value) { cases }} statement (docs/LANGUAGE_SPEC.md section 13).
 * The scrutinee is evaluated once and the cases are tested in source order; exactly the first
 * matching case body executes, and no case falls through. A case body is an implicit block. The
 * statement has no result value, so it is a {@link StatementNode}, not an expression.
 */
public final class SwitchStmtNode extends StatementNode {

    private final ExpressionNode scrutinee;
    private final List<SwitchCaseNode> cases;

    public SwitchStmtNode(ExpressionNode scrutinee, List<SwitchCaseNode> cases, SourceSpan span) {
        super(AstKind.SWITCH_STMT, span);
        this.scrutinee = Objects.requireNonNull(scrutinee);
        this.cases = List.copyOf(Objects.requireNonNull(cases));
    }

    public ExpressionNode scrutinee() {
        return scrutinee;
    }

    public List<SwitchCaseNode> cases() {
        return cases;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>();
        kids.add(scrutinee);
        kids.addAll(cases);
        return List.copyOf(kids);
    }
}
