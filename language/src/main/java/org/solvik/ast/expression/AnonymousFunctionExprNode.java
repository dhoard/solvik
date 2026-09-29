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
 * <p>The capture list is {@link #captures()}: written {@code [base, this]} between {@code func} and the
 * parameter list, and empty when nothing was written. "An empty capture list is a parse error", so an
 * absent list lowers to an empty list rather than to a list holding one empty item, and a non-capturing
 * anonymous function is exactly one whose list is empty. The list is part of the expression but not part
 * of its function type: callers supply the parameters while the expression visibly binds the captured
 * values, so {@code func [factor](value: Integer): Integer} has type {@code func(Integer): Integer}.
 *
 * <p>The items are unresolved syntax. Which binding each names, and whether that binding is eligible to
 * be captured, is decided by semantic analysis at the closure-creation site, which records the resulting
 * captured values on the {@link org.solvik.semantic.FunctionSymbol} this expression denotes.
 */
public final class AnonymousFunctionExprNode extends ExpressionNode {

    private final List<CaptureItem> captures;
    private final List<ParameterNode> parameters;
    private final TypeRef returnType;
    private final BlockNode body;

    public AnonymousFunctionExprNode(List<CaptureItem> captures, List<ParameterNode> parameters, TypeRef returnType, BlockNode body, SourceSpan span) {
        super(AstKind.ANONYMOUS_FUNCTION_EXPR, span);
        this.captures = List.copyOf(captures);
        this.parameters = List.copyOf(parameters);
        this.returnType = Objects.requireNonNull(returnType);
        this.body = Objects.requireNonNull(body);
    }

    /** The written capture items in source order, empty when no capture list was written. */
    public List<CaptureItem> captures() {
        return captures;
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
        // A capture item is a record and not an AstNode: it is one unresolved name, so it has no
        // children of its own and nothing traverses into it. The semantic layer reaches the items
        // through captures() rather than through the tree.
        List<AstNode> kids = new ArrayList<>(parameters);
        kids.add(returnType);
        kids.add(body);
        return List.copyOf(kids);
    }
}
