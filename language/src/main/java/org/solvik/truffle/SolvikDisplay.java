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
package org.solvik.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString;
import org.solvik.truffle.object.SolvikEnumValue;
import org.solvik.truffle.object.SolvikBuiltinCollection;
import org.solvik.truffle.object.SolvikAny;
import org.solvik.truffle.object.SolvikRegex;
import org.solvik.truffle.object.SolvikRegexMatch;

/**
 * Renders Solvik values for {@code print}/{@code println} and the built-in {@code toString}
 * (docs/LANGUAGE_SPEC.md sections 4 and 6): strings and characters as their contents, numbers in
 * decimal or Java-style floating-point text, Boolean values as {@code true} or {@code false},
 * {@code Unit} as {@code Unit}, and an ordinary object as its class name.
 */
public final class SolvikDisplay {

    private SolvikDisplay() {
    }

    @TruffleBoundary
    public static String render(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof SolvikUnit) {
            return "Unit";
        }
        if (value instanceof Integer i) {
            return Integer.toString(i);
        }
        if (value instanceof Byte b) {
            return Byte.toString(b);
        }
        if (value instanceof Short s) {
            return Short.toString(s);
        }
        if (value instanceof Long l) {
            return Long.toString(l);
        }
        if (value instanceof Float f) {
            return Float.toString(f);
        }
        if (value instanceof Double d) {
            return Double.toString(d);
        }
        if (value instanceof Character c) {
            return Character.toString(c);
        }
        if (value instanceof Boolean b) {
            return Boolean.toString(b);
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof TruffleString s) {
            return s.toJavaStringUncached();
        }
        if (value instanceof SolvikBuiltinCollection collection) {
            // A built-in collection displays by its class name, like an ordinary object.
            return collection.typeName();
        }
        if (value instanceof SolvikAny object) {
            return object.solvikClass().name();
        }
        if (value instanceof SolvikRegex) {
            // A built-in object displays as its class name, like an ordinary Solvik object.
            return "Regex";
        }
        if (value instanceof SolvikRegexMatch) {
            return "RegexMatch";
        }
        if (value instanceof SolvikEnumValue enumValue) {
            // An enum value belongs to its enum type, so it displays as that type's name, exactly as an
            // ordinary object displays as its class name.
            return enumValue.enumClass().name();
        }
        // Any other runtime value is unreachable for a statically checked program. Avoid a generic
        // Object.toString() call here: pulling every JDK toString() into the native image is
        // rejected by the Truffle native-image feature.
        return "<object>";
    }
}
