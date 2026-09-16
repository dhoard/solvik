/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.ast.expression;

import org.solvik.ast.AstKind;
import org.solvik.source.SourceSpan;

/**
 * A Rust-style raw string literal (docs/LANGUAGE_SPEC.md section 15). {@link #lexeme()} is the
 * complete source spelling including the delimiters; {@link #value()} is the content between them,
 * with no escape processing so backslashes, quotes, and physical newlines are preserved verbatim.
 * {@link #hashCount()} is the number {@code N} in {@code r + N '#' + '"' ... '"' + N '#'}.
 */
public final class RawStringLiteralNode extends LiteralNode {

    private final int hashCount;
    private final String value;

    public RawStringLiteralNode(String lexeme, SourceSpan span) {
        super(AstKind.RAW_STRING_LITERAL, lexeme, span);
        this.hashCount = countHashes(lexeme);
        this.value = lexeme.substring(hashCount + 2, lexeme.length() - hashCount - 1);
    }

    /** The opening delimiter's hash count, zero for {@code r"..."}. */
    public int hashCount() {
        return hashCount;
    }

    /** The raw content between the delimiters, exactly as written. */
    public String value() {
        return value;
    }

    private static int countHashes(String lexeme) {
        int i = 1;
        while (i < lexeme.length() && lexeme.charAt(i) == '#') {
            i++;
        }
        return i - 1;
    }
}
