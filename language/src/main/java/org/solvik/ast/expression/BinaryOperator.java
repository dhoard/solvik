/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.expression;

/**
 * Binary operators of the static core (docs/LANGUAGE_SPEC.md section 3), in ascending grammar
 * precedence. Operators that depend on later phases ({@code ??}, {@code is}, {@code as}) are not
 * part of this set.
 */
public enum BinaryOperator {
    OR("||", Kind.LOGICAL),
    AND("&&", Kind.LOGICAL),
    EQ("==", Kind.EQUALITY),
    NEQ("!=", Kind.EQUALITY),
    LT("<", Kind.COMPARISON),
    LE("<=", Kind.COMPARISON),
    GT(">", Kind.COMPARISON),
    GE(">=", Kind.COMPARISON),
    ADD("+", Kind.ARITHMETIC),
    SUB("-", Kind.ARITHMETIC),
    MUL("*", Kind.ARITHMETIC),
    DIV("/", Kind.ARITHMETIC);

    /** Operator family, used by the type checker to select its typing rule. */
    public enum Kind {
        LOGICAL,
        EQUALITY,
        COMPARISON,
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
