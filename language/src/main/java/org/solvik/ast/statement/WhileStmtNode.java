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

/** A pre-test {@code while (condition) block} loop. The condition must be {@code Boolean}. */
public final class WhileStmtNode extends StatementNode {

    private final ExpressionNode condition;
    private final BlockNode body;

    public WhileStmtNode(ExpressionNode condition, BlockNode body, SourceSpan span) {
        super(AstKind.WHILE_STMT, span);
        this.condition = Objects.requireNonNull(condition);
        this.body = Objects.requireNonNull(body);
    }

    public ExpressionNode condition() {
        return condition;
    }

    public BlockNode body() {
        return body;
    }

    @Override
    public List<AstNode> children() {
        return List.of(condition, body);
    }
}
