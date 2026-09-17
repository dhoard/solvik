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

/** A single typed function parameter, e.g. {@code a: Int}. */
public final class ParameterNode extends AstNode {

    private final String name;
    private final TypeRefNode type;

    public ParameterNode(String name, TypeRefNode type, SourceSpan span) {
        super(AstKind.PARAMETER, span);
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
    }

    public String name() {
        return name;
    }

    public TypeRefNode type() {
        return type;
    }

    @Override
    public List<AstNode> children() {
        return List.of(type);
    }
}
