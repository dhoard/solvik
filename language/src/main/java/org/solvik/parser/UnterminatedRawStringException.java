/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.parser;

import org.antlr.v4.runtime.IntStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

/**
 * Signals an unterminated Rust-style raw string (docs/LANGUAGE_SPEC.md section 15) to {@link
 * SolvikErrorListener}. The lexer's hand-written raw-string helper passes this exception through
 * the ordinary ANTLR error dispatch so that the diagnostic layer can distinguish the precise
 * raw-string problem from a generic lexical error and name the expected closing delimiter.
 */
public final class UnterminatedRawStringException extends RecognitionException {

    private final String expectedClosingDelimiter;

    public UnterminatedRawStringException(Recognizer<?, ?> recognizer, IntStream input, Token openingDelimiter, String expectedClosingDelimiter) {
        super(recognizer, input, null);
        setOffendingToken(openingDelimiter);
        this.expectedClosingDelimiter = expectedClosingDelimiter;
    }

    /** The exact closing delimiter the opening delimiter required, for example {@code "#}. */
    public String expectedClosingDelimiter() {
        return expectedClosingDelimiter;
    }
}
