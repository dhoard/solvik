/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
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
