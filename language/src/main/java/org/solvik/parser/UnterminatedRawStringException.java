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
