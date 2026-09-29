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
package org.solvik.ast.expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.declaration.ParameterNode;
import org.solvik.ast.declaration.TypeRef;
import org.solvik.ast.statement.BlockNode;
import org.solvik.source.SourceSpan;

/**
 * An anonymous function expression (docs/LANGUAGE_SPEC.md section 6, "Anonymous functions"):
 * {@code func(params): Return { body }}. It is an expression whose value is a freshly created function
 * value, not a declaration: it names nothing and is entered into no scope, so the only way to reach its
 * body is through the value this expression produces.
 *
 * <p>Its parameters carry explicit types and its return type follows the declaration rule — an omitted
 * return type names {@code Unit}, which the AST builder synthesizes as a written reference so the
 * semantic layer sees a return type here exactly as it does on a declaration. Its {@link #body()} is a
 * statement block, not a value-required block, because function bodies never acquire an implicit tail
 * result; a value-returning anonymous function returns through {@code return}.
 *
 * <p>The explicit capture list of section 6 ("Explicit immutable closure capture") is not represented
 * here: this revision implements only non-capturing anonymous functions, so a body sees its own
 * parameters, its own locals, and global declarations, and nothing from the enclosing function. The
 * change that adds capture adds the list here, resolves it against the enclosing function, and stores
 * the captured values in the function value the expression creates.
 */
public final class AnonymousFunctionExprNode extends ExpressionNode {

    private final List<ParameterNode> parameters;
    private final TypeRef returnType;
    private final BlockNode body;

    public AnonymousFunctionExprNode(List<ParameterNode> parameters, TypeRef returnType, BlockNode body, SourceSpan span) {
        super(AstKind.ANONYMOUS_FUNCTION_EXPR, span);
        this.parameters = List.copyOf(parameters);
        this.returnType = Objects.requireNonNull(returnType);
        this.body = Objects.requireNonNull(body);
    }

    public List<ParameterNode> parameters() {
        return parameters;
    }

    public TypeRef returnType() {
        return returnType;
    }

    public BlockNode body() {
        return body;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> kids = new ArrayList<>(parameters);
        kids.add(returnType);
        kids.add(body);
        return List.copyOf(kids);
    }
}
