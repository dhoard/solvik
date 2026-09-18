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
import java.util.Objects;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * A {@code key: value} argument written in a call's argument list. The pair is only meaningful as an
 * element of a built-in {@code Map} construction; the semantic pass records the key and value types
 * there and rejects an entry in any other argument list.
 */
public final class MapEntryExprNode extends ExpressionNode {

    private final ExpressionNode key;
    private final ExpressionNode value;

    public MapEntryExprNode(ExpressionNode key, ExpressionNode value, SourceSpan span) {
        super(AstKind.MAP_ENTRY_EXPR, span);
        this.key = Objects.requireNonNull(key);
        this.value = Objects.requireNonNull(value);
    }

    public ExpressionNode key() {
        return key;
    }

    public ExpressionNode value() {
        return value;
    }

    @Override
    public List<AstNode> children() {
        return List.of(key, value);
    }
}
