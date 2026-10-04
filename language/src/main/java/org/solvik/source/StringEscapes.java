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
package org.solvik.source;

import java.util.Optional;

/**
 * Escape processing for normal Solvik string literals (docs/LANGUAGE_SPEC.md section 15). Exactly
 * {@code \\}, {@code \"}, {@code \n}, {@code \r}, {@code \t}, {@code \0}, and {@code \N} are
 * supported; any other escape is a lexical error. Raw strings never pass through here.
 *
 * <p>{@code \N} denotes the <em>target platform's</em> native line separator. Its concrete value is
 * platform dependent, so it must not be frozen into a portable artifact by the compiler. Decoding
 * therefore leaves the {@link #NATIVE_LINE_SEPARATOR_SENTINEL} in the {@link #unescape} result, and
 * the runtime (or compile-time constant evaluator) substitutes the executing host's
 * {@link System#lineSeparator()} through {@link #expandNativeLineSeparators}. This keeps the build
 * host's newline out of compiled artifacts and makes constant folding and execution agree.
 */
public final class StringEscapes {

    /**
     * The single code unit standing in for {@code \N} in a decoded string until the value is observed
     * on the target runtime. It is a rarely used graphic character with no structural role in Solvik
     * lexical syntax: a physical U+00A6 never terminates a token, opens a construct, or ends a
     * physical line, so carrying it across the AST, lowering, and serialization is inert. A
     * program that needs the literal character itself cannot spell it as a Solvik source escape in
     * any case, because {@code \N} is the only mechanism that could produce it before expansion.
     */
    public static final char NATIVE_LINE_SEPARATOR_SENTINEL = '\u00a6';

    private StringEscapes() {
    }

    /**
     * Returns the first unsupported escape sequence in a normal string literal lexeme (including its
     * surrounding quotes), or empty when every escape is supported.
     */
    public static Optional<String> invalidEscape(String lexeme) {
        int end = lexeme.length() - 1;
        for (int i = 1; i < end; i++) {
            char c = lexeme.charAt(i);
            if (c != '\\') {
                continue;
            }
            if (i + 1 >= end) {
                return Optional.of("\\");
            }
            char escaped = lexeme.charAt(i + 1);
            if (!isSupported(escaped)) {
                return Optional.of("\\" + escaped);
            }
            i++;
        }
        return Optional.empty();
    }

    /**
     * Decodes the supported escapes; unsupported ones are preserved verbatim defensively. {@code \N}
     * decodes to {@link #NATIVE_LINE_SEPARATOR_SENTINEL}, not to a concrete newline, so the target
     * runtime supplies the real line separator through {@link #expandNativeLineSeparators}.
     */
    public static String unescape(String lexeme) {
        int end = lexeme.length() - 1;
        StringBuilder result = new StringBuilder(end - 1);
        for (int i = 1; i < end; i++) {
            char c = lexeme.charAt(i);
            if (c == '\\' && i + 1 < end) {
                char escaped = lexeme.charAt(i + 1);
                switch (escaped) {
                    case '\\' -> result.append('\\');
                    case '"' -> result.append('"');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case '0' -> result.append('\0');
                    case 'N' -> result.append(NATIVE_LINE_SEPARATOR_SENTINEL);
                    default -> result.append('\\').append(escaped);
                }
                i++;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Substitutes each {@link #NATIVE_LINE_SEPARATOR_SENTINEL} with {@code nativeLineSeparator}. The
     * caller supplies the separator that is correct for the context: the executing runtime passes
     * {@link System#lineSeparator()}; a compile-time evaluator passes the separator of the target it
     * is evaluating for. Strings without the sentinel are returned unchanged.
     */
    public static String expandNativeLineSeparators(String decoded, String nativeLineSeparator) {
        if (decoded.indexOf(NATIVE_LINE_SEPARATOR_SENTINEL) < 0) {
            return decoded;
        }
        return decoded.replace(String.valueOf(NATIVE_LINE_SEPARATOR_SENTINEL), nativeLineSeparator);
    }

    /** Whether {@code decoded} still carries an unexpanded {@code \N} sentinel. */
    public static boolean containsNativeLineSeparator(String decoded) {
        return decoded.indexOf(NATIVE_LINE_SEPARATOR_SENTINEL) >= 0;
    }

    private static boolean isSupported(char escaped) {
        return escaped == '\\' || escaped == '"' || escaped == 'n' || escaped == 'r' || escaped == 't' || escaped == '0' || escaped == 'N';
    }
}
