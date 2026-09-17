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
 * A decimal floating-point literal with an optional exponent (docs/LANGUAGE_SPEC.md section 1).
 * A trailing {@code f}/{@code F} suffix selects {@code Float}; otherwise the literal has type
 * {@code Double}.
 */
public final class FloatingLiteralNode extends LiteralNode {

    public FloatingLiteralNode(String lexeme, SourceSpan span) {
        super(AstKind.FLOATING_LITERAL, lexeme, span);
    }

    /** Whether the literal carries the {@code f}/{@code F} suffix that selects {@code Float}. */
    public boolean isFloat() {
        String text = lexeme();
        char last = text.charAt(text.length() - 1);
        return last == 'f' || last == 'F';
    }

    /** The literal text without its optional type suffix, suitable for parsing. */
    public String numericText() {
        return isFloat() ? lexeme().substring(0, lexeme().length() - 1) : lexeme();
    }
}
