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
import org.solvik.ast.statement.SwitchCaseNode;
import org.solvik.source.SourceSpan;

/**
 * A {@code switch} expression (docs/LANGUAGE_SPEC.md section 21). It shares the surface syntax of
 * the statement form: the scrutinee is evaluated exactly once, cases are tested in source order,
 * and exactly the first matching body executes with no implicit fallthrough. An expression
 * {@code switch} must contain exactly one {@code default}, which must remain last; every normally
 * completing case body, including {@code default}, must produce a tail result.
 */
public final class SwitchExprNode extends ExpressionNode {

    private final ExpressionNode scrutinee;
    private final List<SwitchCaseNode> cases;

    public SwitchExprNode(ExpressionNode scrutinee, List<SwitchCaseNode> cases, SourceSpan span) {
        super(AstKind.SWITCH_EXPR, span);
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
