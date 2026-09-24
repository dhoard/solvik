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
package org.solvik.ast.declaration;

import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.statement.BlockNode;
import org.solvik.source.SourceSpan;

/**
 * A class initializer {@code static { ... }} (docs/LANGUAGE_SPEC.md section 7, "Static members and
 * class initialization"): a statement list in a block that runs once per class at program start,
 * after the class's static property declaration initializers and after the initializer of any
 * superclass. A class declares at most one; the second is {@code SOLV-SEM-046}. The body is a
 * {@link BlockNode}, so the block's statements and terminators behave exactly like any other block.
 */
public final class StaticBlockNode extends AstNode {

    private final BlockNode body;

    public StaticBlockNode(BlockNode body, SourceSpan span) {
        super(AstKind.STATIC_BLOCK, span);
        this.body = Objects.requireNonNull(body);
    }

    public BlockNode body() {
        return body;
    }

    @Override
    public List<AstNode> children() {
        return List.of(body);
    }
}
