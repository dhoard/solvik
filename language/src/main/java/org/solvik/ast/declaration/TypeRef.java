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
 * The common abstraction for a written type reference. A declared-type position stores a
 * {@link TypeRef} so it can carry either a nominal {@link TypeRefNode} or a
 * {@link FunctionTypeRefNode}; the grammar permits a function type to be written anywhere a type is,
 * and the semantic layer rejects the ones that are not legal (docs/LANGUAGE_SPEC.md section 11).
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

    /**
     * The display name of the written reference: a nominal reference's type name, or {@code func} for
     * a function type. Both spellings are the head name a diagnostic would show for the reference
     * itself; a function type's full {@code func(P): R} rendering lives on the resolved
     * {@link org.solvik.type.FunctionType}, not on the syntax node.
     */
    public abstract String name();

    /**
     * Whether the whole written reference carries a trailing {@code ?}: a nominal {@code T?} or the
     * grouped nullable function form {@code (func(...): R)?}. Nullability of a function type's result
     * is separate and lives on its return reference.
     */
    public abstract boolean isNullable();
}
