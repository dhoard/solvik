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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.statement.BlockNode;
import org.solvik.source.SourceSpan;

/**
 * A class constructor declaration {@code Name(parameters) { ... }} (docs/LANGUAGE_SPEC.md section
 * 7). The declaration carries no {@code func} keyword and no return type, and {@link #name()} must
 * equal the enclosing class name; calling the class name invokes it. A class has at most one
 * constructor.
 */
public final class ConstructorDeclNode extends AstNode {

    private final String name;
    private final List<ParameterNode> parameters;
    private final BlockNode body;

    public ConstructorDeclNode(String name, List<ParameterNode> parameters, BlockNode body, SourceSpan span) {
        super(AstKind.CONSTRUCTOR_DECL, span);
        this.name = Objects.requireNonNull(name);
        this.parameters = List.copyOf(parameters);
        this.body = Objects.requireNonNull(body);
    }

    /** The declared name, which must equal the enclosing class name. */
    public String name() {
        return name;
    }

    public List<ParameterNode> parameters() {
        return parameters;
    }

    public BlockNode body() {
        return body;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>(parameters);
        kids.add(body);
        return List.copyOf(kids);
    }
}
