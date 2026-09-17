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
 * One declared type parameter of a generic declaration, e.g. {@code T} in {@code class Box<T>}
 * (docs/LANGUAGE_SPEC.md section 11). The initial language has no bounds, so a type parameter is a
 * bare name.
 */
public final class TypeParameterNode extends AstNode {

    private final String name;

    public TypeParameterNode(String name, SourceSpan span) {
        super(AstKind.TYPE_PARAMETER, span);
        this.name = Objects.requireNonNull(name);
    }

    public String name() {
        return name;
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}
