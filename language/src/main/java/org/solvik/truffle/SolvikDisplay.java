/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle;

import com.oracle.truffle.api.strings.TruffleString;
import org.solvik.truffle.object.SolvikEnumValue;
import org.solvik.truffle.object.SolvikObject;
import org.solvik.truffle.object.SolvikRegex;
import org.solvik.truffle.object.SolvikRegexMatch;

/**
 * Renders Solvik values for {@code print}/{@code println} (docs/LANGUAGE_SPEC.md section 6):
 * strings and characters as their contents, numbers in decimal, Boolean values as {@code true} or
 * {@code false}, {@code Unit} as {@code Unit}, and an ordinary object as its class name.
 */
final class SolvikDisplay {

    private SolvikDisplay() {
    }

    static String render(Object value) {
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
        if (value instanceof SolvikObject object) {
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
