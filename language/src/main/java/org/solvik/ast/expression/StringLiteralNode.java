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
 * A normal (non-raw) string literal. {@link #lexeme()} includes the surrounding quotes; escape
 * validation and unescaping belong to lexical hardening in later phases.
 */
public final class StringLiteralNode extends LiteralNode {

    public StringLiteralNode(String lexeme, SourceSpan span) {
        super(AstKind.STRING_LITERAL, lexeme, span);
    }
}
