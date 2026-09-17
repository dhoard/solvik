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

/**
 * The range operator of a {@code for (name in start <op> end)} loop
 * (docs/LANGUAGE_SPEC.md section 17).
 *
 * <ul>
 * <li>{@code ...} ascends from {@code start} and includes {@code end};</li>
 * <li>{@code ..<} ascends from {@code start} and excludes {@code end};</li>
 * <li>{@code ..>} descends from {@code start} and excludes {@code end}.</li>
 * </ul>
 *
 * <p>A reversed or empty range performs zero iterations, so none of the operators is an error on
 * that account.
 */
public enum RangeOperator {
    /** {@code start...end}: ascending and inclusive on both ends. */
    INCLUSIVE("..."),
    /** {@code start..<end}: ascending, excluding {@code end}. */
    EXCLUSIVE_ASCENDING("..<"),
    /** {@code start..>end}: descending, excluding {@code end}. */
    EXCLUSIVE_DESCENDING("..>");

    private final String spelling;

    RangeOperator(String spelling) {
        this.spelling = spelling;
    }

    /** The source spelling of this operator. */
    public String spelling() {
        return spelling;
    }

    /** Whether the range ascends toward {@code end}. */
    public boolean isAscending() {
        return this != EXCLUSIVE_DESCENDING;
    }

    /** Maps a source spelling to its operator. */
    public static RangeOperator fromSpelling(String spelling) {
        for (RangeOperator operator : values()) {
            if (operator.spelling.equals(spelling)) {
                return operator;
            }
        }
        throw new IllegalArgumentException("not a range operator: " + spelling);
    }
}
