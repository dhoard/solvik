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

import org.solvik.ast.AstKind;
import org.solvik.source.SourceSpan;

/**
 * A {@code Long} literal, written with an {@code L}/{@code l} suffix (docs/LANGUAGE_SPEC.md
 * section 1). The lexeme includes the suffix; range checking against signed 64-bit is a static
 * diagnostic.
 */
public final class LongLiteralNode extends LiteralNode {

    public LongLiteralNode(String lexeme, SourceSpan span) {
        super(AstKind.LONG_LITERAL, lexeme, span);
    }
}
