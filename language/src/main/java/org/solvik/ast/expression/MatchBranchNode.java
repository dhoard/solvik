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
import org.solvik.ast.AstNode;
import org.solvik.ast.pattern.PatternNode;
import org.solvik.source.SourceSpan;

/**
 * One {@code pattern => result} branch of a {@code match} expression
 * (docs/LANGUAGE_SPEC.md section 12). The pattern is matched against the scrutinee and the result
 * expression is evaluated when it matches; the branch itself is not a value-producing expression,
 * so it extends {@link AstNode} rather than {@link ExpressionNode}.
 */
public final class MatchBranchNode extends AstNode {

    private final PatternNode pattern;
    private final ExpressionNode result;

    public MatchBranchNode(PatternNode pattern, ExpressionNode result, SourceSpan span) {
        super(org.solvik.ast.AstKind.MATCH_BRANCH, span);
        this.pattern = Objects.requireNonNull(pattern);
        this.result = Objects.requireNonNull(result);
    }

    public PatternNode pattern() {
        return pattern;
    }

    public ExpressionNode result() {
        return result;
    }

    @Override
    public List<AstNode> children() {
        ArrayList<AstNode> kids = new ArrayList<>();
        kids.add(pattern);
        kids.add(result);
        return List.copyOf(kids);
    }
}
