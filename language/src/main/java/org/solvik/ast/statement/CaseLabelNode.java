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

import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.source.SourceSpan;

/**
 * Base class of the two {@code switch} case label forms (docs/LANGUAGE_SPEC.md section 13): a
 * compile-time constant expression and a {@code regex <string literal>} pattern. A label is a syntax
 * node like any other, carrying a source span and ordered children but no resolved symbol or runtime
 * representation.
 */
public abstract class CaseLabelNode extends AstNode {

    protected CaseLabelNode(AstKind kind, SourceSpan span) {
        super(kind, span);
    }
}
