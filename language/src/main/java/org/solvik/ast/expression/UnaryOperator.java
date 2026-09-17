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

/** Prefix operators of the static core (docs/LANGUAGE_SPEC.md section 3). */
public enum UnaryOperator {
    /** Logical negation {@code !}, which requires a {@code Boolean} operand. */
    NOT("!"),
    /** Arithmetic negation {@code -}, which requires an {@code Int} operand. */
    NEGATE("-");

    private final String spelling;

    UnaryOperator(String spelling) {
        this.spelling = spelling;
    }

    public String spelling() {
        return spelling;
    }

    /** Maps a source spelling to its operator, or {@code null} when it is not a unary operator. */
    public static UnaryOperator fromSpelling(String spelling) {
        for (UnaryOperator op : values()) {
            if (op.spelling.equals(spelling)) {
                return op;
            }
        }
        return null;
    }
}
