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
package org.solvik.ast.statement;

import java.util.List;
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.expression.ExpressionNode;
import org.solvik.source.SourceSpan;

/**
 * A {@code regex <pattern>} {@code switch} case label (docs/LANGUAGE_SPEC.md sections 13 and 14),
 * for example {@code case regex r#"^\d+$"#:}. The pattern is a normal or raw string literal
 * expression; the static semantic pass compiles it through the portable regex dialect and records
 * the compiled pattern so lowering reuses it for every execution.
 */
public final class RegexCaseLabelNode extends CaseLabelNode {

    private final ExpressionNode pattern;

    public RegexCaseLabelNode(ExpressionNode pattern, SourceSpan span) {
        super(AstKind.REGEX_CASE_LABEL, span);
        this.pattern = Objects.requireNonNull(pattern);
    }

    /** The string literal pattern, a {@code StringLiteralNode} or a {@code RawStringLiteralNode}. */
    public ExpressionNode pattern() {
        return pattern;
    }

    @Override
    public List<AstNode> children() {
        return List.of(pattern);
    }
}
