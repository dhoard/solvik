/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.expression;

import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * A member access {@code receiver.member} or, from Phase 10, its safe form
 * {@code receiver?.member} (docs/LANGUAGE_SPEC.md section 5). A safe access evaluates to
 * {@code null} without touching the member when the receiver is {@code null}, so the compiler
 * makes the result type nullable when the receiver is nullable.
 */
public final class MemberAccessExprNode extends ExpressionNode {

    private final ExpressionNode receiver;
    private final String memberName;
    private final boolean safe;

    public MemberAccessExprNode(ExpressionNode receiver, String memberName, SourceSpan span) {
        this(receiver, memberName, false, span);
    }

    public MemberAccessExprNode(ExpressionNode receiver, String memberName, boolean safe, SourceSpan span) {
        super(AstKind.MEMBER_ACCESS_EXPR, span);
        this.receiver = Objects.requireNonNull(receiver);
        this.memberName = Objects.requireNonNull(memberName);
        this.safe = safe;
    }

    public ExpressionNode receiver() {
        return receiver;
    }

    public String memberName() {
        return memberName;
    }

    /** Whether the access was written with {@code ?.} rather than {@code .}. */
    public boolean isSafe() {
        return safe;
    }

    @Override
    public List<AstNode> children() {
        return List.of(receiver);
    }
}
