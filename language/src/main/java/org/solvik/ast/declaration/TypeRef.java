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

import org.solvik.ast.AstNode;

/**
 * The common abstraction for a written type reference (docs/LANGUAGE_SPEC.md sections 5 and 11).
 * {@link TypeRefNode} is currently the only concrete kind: a nominal name with optional module
 * prefix, optional generic type arguments, and an optional nullable marker. Functions are
 * declarations rather than values, so no function type can be written.
 *
 * <p>It is an abstract class rather than an interface because a type reference is also an AST child
 * (it participates in {@link AstNode#children()}), so the abstraction must itself be an
 * {@link AstNode}. This mirrors the other front-end families ({@code ExpressionNode},
 * {@code StatementNode}, {@code CallableDeclNode}).
 */
public abstract class TypeRef extends AstNode {

    protected TypeRef(org.solvik.ast.AstKind kind, org.solvik.source.SourceSpan span) {
        super(kind, span);
    }

    /** The type name the reference writes. */
    public abstract String name();

    /** Whether the whole written reference carries a trailing {@code ?}, as in {@code T?}. */
    public abstract boolean isNullable();
}
