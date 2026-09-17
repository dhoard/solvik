/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.solvik.ast.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * An expression-oriented {@code match <scrutinee> { branches }} (docs/LANGUAGE_SPEC.md section 12).
 * The scrutinee is a value-producing expression and each branch pairs a pattern with a result
 * expression. The compiler checks that the branches are exhaustive for a known closed variant set
 * and that every branch result is assignable to the match's nearest common declared supertype.
 */
public final class MatchExprNode extends ExpressionNode {

    private final ExpressionNode scrutinee;
    private final List<MatchBranchNode> branches;

    public MatchExprNode(ExpressionNode scrutinee, List<MatchBranchNode> branches, SourceSpan span) {
        super(AstKind.MATCH_EXPR, span);
        this.scrutinee = Objects.requireNonNull(scrutinee);
        this.branches = List.copyOf(Objects.requireNonNull(branches));
    }

    public ExpressionNode scrutinee() {
        return scrutinee;
    }

    public List<MatchBranchNode> branches() {
        return branches;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>();
        kids.add(scrutinee);
        kids.addAll(branches);
        return List.copyOf(kids);
    }
}
