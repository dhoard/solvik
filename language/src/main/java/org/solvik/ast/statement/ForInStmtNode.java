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
package org.solvik.ast.statement;

import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.source.SourceSpan;

/**
 * A range {@code for}-in loop: {@code for (name in start <op> end) block}
 * (docs/LANGUAGE_SPEC.md section 17). The loop variable is an implicitly declared immutable
 * {@code Int} binding scoped to the body. The two bounds are {@code Int} expressions evaluated once
 * before the first iteration.
 */
public final class ForInStmtNode extends StatementNode {

    private final String variableName;
    private final RangeOperator operator;
    private final ExpressionNode start;
    private final ExpressionNode end;
    private final BlockNode body;

    public ForInStmtNode(String variableName, RangeOperator operator, ExpressionNode start, ExpressionNode end, BlockNode body, SourceSpan span) {
        super(AstKind.FOR_IN_STMT, span);
        this.variableName = Objects.requireNonNull(variableName);
        this.operator = Objects.requireNonNull(operator);
        this.start = Objects.requireNonNull(start);
        this.end = Objects.requireNonNull(end);
        this.body = Objects.requireNonNull(body);
    }

    public String variableName() {
        return variableName;
    }

    public RangeOperator operator() {
        return operator;
    }

    public ExpressionNode start() {
        return start;
    }

    public ExpressionNode end() {
        return end;
    }

    public BlockNode body() {
        return body;
    }

    @Override
    public List<AstNode> children() {
        return List.of(start, end, body);
    }
}
