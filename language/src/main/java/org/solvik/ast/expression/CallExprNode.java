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
import org.solvik.source.SourceSpan;

/** A call expression {@code callee(argument, ...)}; the callee is any expression. */
public final class CallExprNode extends ExpressionNode {

    private final ExpressionNode callee;
    private final List<ExpressionNode> arguments;

    public CallExprNode(ExpressionNode callee, List<ExpressionNode> arguments, SourceSpan span) {
        super(AstKind.CALL_EXPR, span);
        this.callee = Objects.requireNonNull(callee);
        this.arguments = List.copyOf(arguments);
    }

    public ExpressionNode callee() {
        return callee;
    }

    public List<ExpressionNode> arguments() {
        return arguments;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>();
        kids.add(callee);
        kids.addAll(arguments);
        return List.copyOf(kids);
    }
}
