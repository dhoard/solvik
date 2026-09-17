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

import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * Base class of the initial {@code match} pattern forms (docs/LANGUAGE_SPEC.md section 12): an enum
 * variant pattern, a sealed-subtype binding pattern of the form {@code name: Type}, and the
 * wildcard {@code _}. A pattern is a syntax node exactly like any other AST node, so it carries a
 * source span and ordered children but no resolved symbol or runtime representation.
 */
public abstract class PatternNode extends AstNode {

    protected PatternNode(AstKind kind, SourceSpan span) {
        super(kind, span);
    }
}
