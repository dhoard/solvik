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
import java.util.Optional;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * The {@code else} part of an if statement: either a nested {@link IfStmtNode} (chained
 * {@code else if}) or a replacement {@link BlockNode}.
 */
public final class ElseBranchNode extends AstNode {

    private final IfStmtNode chainedIf;
    private final BlockNode block;

    private ElseBranchNode(IfStmtNode chainedIf, BlockNode block, SourceSpan span) {
        super(AstKind.ELSE_BRANCH, span);
        this.chainedIf = chainedIf;
        this.block = block;
    }

    public static ElseBranchNode ofBlock(BlockNode block, SourceSpan span) {
        return new ElseBranchNode(null, Objects.requireNonNull(block), span);
    }

    public static ElseBranchNode ofChainedIf(IfStmtNode chainedIf, SourceSpan span) {
        return new ElseBranchNode(Objects.requireNonNull(chainedIf), null, span);
    }

    public boolean isChainedIf() {
        return chainedIf != null;
    }

    public Optional<IfStmtNode> chainedIf() {
        return Optional.ofNullable(chainedIf);
    }

    public Optional<BlockNode> block() {
        return Optional.ofNullable(block);
    }

    @Override
    public List<AstNode> children() {
        return chainedIf != null ? List.of(chainedIf) : List.of(block);
    }
}
