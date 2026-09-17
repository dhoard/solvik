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
 * A top-level {@code module <name>} declaration (docs/LANGUAGE_SPEC.md section 20). It is a syntax
 * directive that names the namespace of the physical file it appears in; it is not a declaration or
 * an executable statement and contributes no item to the resolved program.
 *
 * <p>The written name is a single identifier. The lowercase, underscore-separated naming rule and
 * the reserved-word check are enforced during include resolution, not here, so the node records the
 * source spelling unchanged.
 */
public final class ModuleDeclNode extends AstNode {

    private final String name;

    public ModuleDeclNode(String name, SourceSpan span) {
        super(AstKind.MODULE_DECL, span);
        this.name = Objects.requireNonNull(name);
    }

    /** The written module name, still in its source spelling. */
    public String name() {
        return name;
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}
