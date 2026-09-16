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

/** An ordinary member access {@code receiver.member}. Safe-access {@code ?.} is a later phase. */
public final class MemberAccessExprNode extends ExpressionNode {

    private final ExpressionNode receiver;
    private final String memberName;

    public MemberAccessExprNode(ExpressionNode receiver, String memberName, SourceSpan span) {
        super(AstKind.MEMBER_ACCESS_EXPR, span);
        this.receiver = Objects.requireNonNull(receiver);
        this.memberName = Objects.requireNonNull(memberName);
    }

    public ExpressionNode receiver() {
        return receiver;
    }

    public String memberName() {
        return memberName;
    }

    @Override
    public List<AstNode> children() {
        return List.of(receiver);
    }
}
