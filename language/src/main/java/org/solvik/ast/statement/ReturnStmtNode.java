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
import java.util.Optional;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.source.SourceSpan;

/**
 * A {@code return;} or {@code return expression;} statement. Whether a bare return is legal
 * depends on the enclosing function's return type, which Phase 4 checks.
 */
public final class ReturnStmtNode extends StatementNode {

    private final ExpressionNode value;

    public ReturnStmtNode(ExpressionNode value, SourceSpan span) {
        super(AstKind.RETURN_STMT, span);
        this.value = value;
    }

    public Optional<ExpressionNode> value() {
        return Optional.ofNullable(value);
    }

    @Override
    public List<AstNode> children() {
        return value == null ? List.of() : List.of(value);
    }
}
