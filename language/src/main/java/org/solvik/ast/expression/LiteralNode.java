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

import java.util.List;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/** Base class for literal expressions carrying their source spelling. */
public abstract class LiteralNode extends ExpressionNode {

    private final String lexeme;

    protected LiteralNode(AstKind kind, String lexeme, SourceSpan span) {
        super(kind, span);
        this.lexeme = lexeme;
    }

    /** Exact source text of the literal, including quotes for strings. */
    public final String lexeme() {
        return lexeme;
    }

    @Override
    public final List<AstNode> children() {
        return List.of();
    }
}
