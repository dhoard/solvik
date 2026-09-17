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

/**
 * Binary operators of the static core (docs/LANGUAGE_SPEC.md section 3), in ascending grammar
 * precedence. Phase 10 adds {@code ??}, whose left operand must be nullable; {@code is} and
 * {@code as} are not binary operators because their right operand is a written type, so they have
 * their own {@link org.solvik.ast.AstKind#TYPE_TEST_EXPR} and {@link org.solvik.ast.AstKind#CAST_EXPR}
 * nodes. {@code ..} is string concatenation, which binds looser than arithmetic but tighter than
 * comparison (docs/LANGUAGE_SPEC.md section 3).
 */
public enum BinaryOperator {
    // Null coalescing binds loosest of all, below `||`.
    COALESCE("??", Kind.COALESCE),
    OR("||", Kind.LOGICAL),
    AND("&&", Kind.LOGICAL),
    EQ("==", Kind.EQUALITY),
    NEQ("!=", Kind.EQUALITY),
    LT("<", Kind.COMPARISON),
    LE("<=", Kind.COMPARISON),
    GT(">", Kind.COMPARISON),
    GE(">=", Kind.COMPARISON),
    CONCAT("..", Kind.CONCAT),
    ADD("+", Kind.ARITHMETIC),
    SUB("-", Kind.ARITHMETIC),
    MUL("*", Kind.ARITHMETIC),
    DIV("/", Kind.ARITHMETIC);

    /** Operator family, used by the type checker to select its typing rule. */
    public enum Kind {
        COALESCE,
        LOGICAL,
        EQUALITY,
        COMPARISON,
        CONCAT,
        ARITHMETIC
    }

    private final String spelling;
    private final Kind kind;

    BinaryOperator(String spelling, Kind kind) {
        this.spelling = spelling;
        this.kind = kind;
    }

    public String spelling() {
        return spelling;
    }

    public Kind kind() {
        return kind;
    }

    /** Maps a source spelling to its operator, or {@code null} when there is no such operator. */
    public static BinaryOperator fromSpelling(String spelling) {
        for (BinaryOperator op : values()) {
            if (op.spelling.equals(spelling)) {
                return op;
            }
        }
        return null;
    }
}
