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
 * {@code \\}, {@code \"}, {@code \n}, {@code \r}, {@code \t}, and {@code \0} are supported; any
 * other escape is a lexical error. Raw strings never pass through here.
 */
public final class StringEscapes {

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

    /** Decodes the supported escapes; unsupported ones are preserved verbatim defensively. */
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
                    default -> result.append('\\').append(escaped);
                }
                i++;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static boolean isSupported(char escaped) {
        return escaped == '\\' || escaped == '"' || escaped == 'n' || escaped == 'r' || escaped == 't' || escaped == '0';
    }
}
