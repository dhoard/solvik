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
package org.solvik.ast.pattern;

import java.util.List;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * The wildcard pattern {@code _} (docs/LANGUAGE_SPEC.md section 12). It matches every value and
 * binds no name, so its presence makes a {@code match} exhaustive regardless of the closed variant
 * set.
 */
public final class WildcardPatternNode extends PatternNode {

    public WildcardPatternNode(SourceSpan span) {
        super(AstKind.WILDCARD_PATTERN, span);
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }
}
