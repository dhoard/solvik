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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.source.SourceSpan;

/** An {@code if (condition) block [else ...]} statement. Conditions must be {@code Boolean} in Phase 4. */
public final class IfStmtNode extends StatementNode {

    private final ExpressionNode condition;
    private final BlockNode thenBlock;
    private final ElseBranchNode elseBranch;

    public IfStmtNode(ExpressionNode condition, BlockNode thenBlock, ElseBranchNode elseBranch, SourceSpan span) {
        super(AstKind.IF_STMT, span);
        this.condition = Objects.requireNonNull(condition);
        this.thenBlock = Objects.requireNonNull(thenBlock);
        this.elseBranch = elseBranch;
    }

    public ExpressionNode condition() {
        return condition;
    }

    public BlockNode thenBlock() {
        return thenBlock;
    }

    public Optional<ElseBranchNode> elseBranch() {
        return Optional.ofNullable(elseBranch);
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>();
        kids.add(condition);
        kids.add(thenBlock);
        if (elseBranch != null) {
            kids.add(elseBranch);
        }
        return List.copyOf(kids);
    }
}
