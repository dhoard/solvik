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
import org.solvik.source.SourceSpan;

/**
 * A {@code module Name { ... }} block (docs/LANGUAGE_SPEC.md section 20): a named namespace whose
 * declarations belong to that module rather than to the implicit default module.
 *
 * <p>A physical file may hold any number of blocks, and several blocks with one name — in one file or
 * across included files — contribute to the same module. The block is declaration-only: it holds
 * {@code func}, {@code class}, {@code interface}, {@code enum}, and {@code error} declarations, and
 * the grammar admits neither a statement, an {@code include}, nor a nested module inside it.
 *
 * <p>The written name is a single identifier. The lowercase, underscore-separated naming rule is
 * enforced during include resolution, not here, so the node records the source spelling unchanged.
 */
public final class ModuleBlockNode extends AstNode {

    private final String name;
    private final List<DeclarationNode> members;

    public ModuleBlockNode(String name, List<DeclarationNode> members, SourceSpan span) {
        super(AstKind.MODULE_BLOCK, span);
        this.name = Objects.requireNonNull(name);
        this.members = List.copyOf(members);
    }

    /** The written module name, still in its source spelling. */
    public String name() {
        return name;
    }

    /** The declarations of this block, in source order. */
    public List<DeclarationNode> members() {
        return members;
    }

    @Override
    public List<AstNode> children() {
        return List.copyOf(members);
    }
}
