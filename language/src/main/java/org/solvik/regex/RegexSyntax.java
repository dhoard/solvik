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
package org.solvik.regex;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The Solvik regular-expression dialect (docs/LANGUAGE_SPEC.md section 14). Solvik exposes a
 * portable pattern subset rather than an engine-specific syntax: literals, {@code .}, {@code ^},
 * {@code $}, character classes, capturing groups, alternation, {@code *}, {@code +}, {@code ?},
 * {@code {m}}, {@code {m,}}, {@code {m,n}}, and the ASCII classes {@code \d}, {@code \s}, and
 * {@code \w} with their uppercase negations.
 *
 * <p>Backreferences, lookaround, embedded flags, non-capturing and named groups, and other
 * engine-specific extensions are rejected here, before the underlying engine sees the pattern. The
 * dialect lives behind this class so the engine may change without changing language syntax
 * (docs/ARCHITECTURE.md "Regex"): this class is used by static analysis to diagnose an invalid
 * constant pattern and by the runtime to raise a Solvik regex error for a dynamic one.
 */
public final class RegexSyntax {

    private RegexSyntax() {
    }

    /** A pattern that is invalid or outside the portable dialect. */
    public static final class InvalidPatternException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        InvalidPatternException(String message) {
            super(message);
        }
    }

    /**
     * Compiles {@code pattern} after validating that it stays inside the portable dialect. Throws
     * {@link InvalidPatternException} with a concise, user-facing message when the pattern uses an
     * unsupported construct or is otherwise invalid.
     */
    public static RegexPattern compile(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        Optional<String> unsupported = unsupported(pattern);
        if (unsupported.isPresent()) {
            throw new InvalidPatternException(unsupported.get());
        }
        try {
            return new RegexPattern(pattern, Pattern.compile(pattern));
        } catch (PatternSyntaxException e) {
            throw new InvalidPatternException(describe(e));
        }
    }

    /**
     * Returns a message describing the first portable-dialect violation, or empty when the pattern
     * uses only supported constructs. The check is syntactic; a pattern that passes may still be
     * rejected by the engine because of an illegal range or repetition, which {@link #compile}
     * reports.
     */
    public static Optional<String> unsupported(String pattern) {
        Objects.requireNonNull(pattern, "pattern");
        try {
            new Subset(pattern).parse();
            return Optional.empty();
        } catch (InvalidPatternException e) {
            return Optional.of(e.getMessage());
        }
    }

    private static String describe(PatternSyntaxException e) {
        String description = e.getDescription();
        if (e.getIndex() >= 0) {
            return description + " at pattern index " + e.getIndex();
        }
        return description;
    }

    /**
     * A recursive-descent validator over the portable grammar. It mirrors the engine's structural
     * rules closely enough that any accepted pattern compiles, while rejecting every construct the
     * dialect excludes.
     */
    private static final class Subset {

        private final String pattern;
        private int index;

        Subset(String pattern) {
            this.pattern = pattern;
        }

        void parse() {
            alternation();
            if (index != pattern.length()) {
                throw error("unexpected '" + pattern.charAt(index) + "'");
            }
        }

        private void alternation() {
            concatenation();
            while (peek() == '|') {
                index++;
                concatenation();
            }
        }

        private void concatenation() {
            while (index < pattern.length()) {
                char c = pattern.charAt(index);
                if (c == '|' || c == ')') {
                    return;
                }
                repeated();
            }
        }

        private void repeated() {
            atom();
            char c = peek();
            if (c == '*' || c == '+' || c == '?') {
                index++;
            } else if (c == '{') {
                quantifier();
            }
        }

        private void quantifier() {
            index++; // '{'
            if (!isDigit(peek())) {
                throw error("invalid repetition");
            }
            long min = digits();
            long max = min;
            if (peek() == ',') {
                index++;
                max = isDigit(peek()) ? digits() : -1;
            }
            if (peek() != '}') {
                throw error("invalid repetition");
            }
            index++;
            if (max >= 0 && max < min) {
                throw error("repetition range {" + min + "," + max + "} is reversed");
            }
        }

        private long digits() {
            long value = 0;
            while (isDigit(peek())) {
                value = value * 10 + (peek() - '0');
                if (value > Integer.MAX_VALUE) {
                    throw error("repetition count is too large");
                }
                index++;
            }
            return value;
        }

        private void atom() {
            char c = pattern.charAt(index);
            switch (c) {
                case '(' -> {
                    if (peekAt(index + 1) == '?') {
                        throw error("lookaround, flags, non-capturing and named groups are not supported");
                    }
                    index++;
                    alternation();
                    if (peek() != ')') {
                        throw error("unclosed group");
                    }
                    index++;
                }
                case '[' -> characterClass();
                case '.', '^', '$' -> index++;
                case '\\' -> escape();
                case '*', '+', '?' -> throw error("quantifier '" + c + "' has nothing to repeat");
                case '{' -> throw error("invalid repetition");
                case ']' -> throw error("unmatched ']'");
                case '}' -> throw error("unmatched '}'");
                case ')' -> throw error("unmatched ')'");
                default -> index++;
            }
        }

        private void characterClass() {
            index++; // '['
            if (peek() == '^') {
                index++;
            }
            boolean any = false;
            while (index < pattern.length() && peek() != ']') {
                char c = pattern.charAt(index);
                if (c == '\\') {
                    escape();
                } else if (c == '&' && peekAt(index + 1) == '&') {
                    throw error("character class intersection is not supported");
                } else {
                    index++;
                }
                any = true;
            }
            if (!any || peek() != ']') {
                throw error("unclosed character class");
            }
            index++;
        }

        private void escape() {
            index++; // '\'
            if (index >= pattern.length()) {
                throw error("trailing backslash");
            }
            char escaped = pattern.charAt(index);
            if (isSupportedEscape(escaped)) {
                index++;
                return;
            }
            throw error("unsupported escape '\\" + escaped + "'");
        }

        private static boolean isSupportedEscape(char escaped) {
            return switch (escaped) {
                // The ASCII classes named by the specification and their negations.
                case 'd', 'D', 's', 'S', 'w', 'W' -> true;
                // Literal control characters a pattern may need to name.
                case 'n', 'r', 't' -> true;
                default -> !Character.isLetterOrDigit(escaped) && escaped != '_';
            };
        }

        private char peek() {
            return peekAt(index);
        }

        private char peekAt(int position) {
            return position < pattern.length() ? pattern.charAt(position) : '\0';
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private InvalidPatternException error(String message) {
            return new InvalidPatternException(message + " at pattern index " + index);
        }
    }
}
