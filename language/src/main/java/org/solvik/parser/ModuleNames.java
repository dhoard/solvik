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

import java.util.regex.Pattern;

/**
 * The lexical rule for module and alias names (docs/LANGUAGE_SPEC.md section 20): a single
 * identifier that starts with a lowercase letter, contains lowercase letters and digits, and joins
 * parts with exactly one underscore, each part starting with a letter. Examples: {@code com_example_util},
 * {@code utf8}, {@code foo_bar}. Dotted module names are deliberately rejected so a qualified
 * reference is always exactly {@code prefix.Name} and can never be confused with member access.
 *
 * <p>The rule is a strict subset of the Solvik identifier grammar, so it cannot be enforced by the
 * lexer; it is validated where a module or alias name appears.
 */
public final class ModuleNames {

    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9]*(_[a-z0-9]+)*");

    private ModuleNames() {
    }

    /** Whether {@code name} is a valid module or alias name. */
    public static boolean isValid(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /**
     * The canonical {@code SOLV-RESOL-012} message for a rejected name. {@code kind} is {@code module}
     * or {@code alias}. It is centralized here so every declaration site reports one wording.
     */
    public static String invalidNameMessage(String kind, String name) {
        return "invalid " + kind + " name '" + name + "'; expected lowercase letters and digits joined by single underscores";
    }
}
