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
package org.solvik.launcher;

import org.graalvm.polyglot.Value;

/**
 * Serializes the structured compile diagnostics carried by a guest {@code SolvikParseException} into
 * one compact JSON line, so a machine-readable consumer (the TCK adapters) reads stable {@code SOLV-*}
 * codes and source locations as structured fields rather than parsing human-readable stderr.
 *
 * <p>The JSON records each diagnostic's family, code, message, source file, character offsets, and
 * 1-based line/column positions exactly as the guest exposes them through interop. Converting the
 * character offsets to the protocol's UTF-8 byte-offset convention is deliberately left to the
 * adapter, which owns the exact bytes of every staged file; the launcher never assumes a host
 * encoding for offsets it did not compute.
 */
public final class DiagnosticsJson {

    private DiagnosticsJson() {
    }

    /**
     * Builds a {@code COMPILE_ERROR} object with a {@code diagnostics} array read from {@code
     * diagnostics}, an interop array value whose elements expose the diagnostic member names written by
     * the language. {@code entryFile} is the name of the source the compilation was requested for.
     */
    public static String compileError(String entryFile, Value diagnostics) {
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"phase\":\"compile\",");
        json.append("\"status\":\"COMPILE_ERROR\",");
        json.append("\"entryFile\":").append(jsonString(entryFile)).append(',');
        json.append("\"diagnostics\":[");
        long count = diagnostics.getArraySize();
        for (long i = 0; i < count; i++) {
            if (i > 0) {
                json.append(',');
            }
            Value d = diagnostics.getArrayElement(i);
            json.append('{');
            json.append("\"family\":").append(jsonString(stringMember(d, "family"))).append(',');
            json.append("\"code\":").append(jsonString(stringMember(d, "code"))).append(',');
            json.append("\"text\":").append(jsonString(stringMember(d, "text"))).append(',');
            json.append("\"file\":").append(jsonString(stringMember(d, "file"))).append(',');
            json.append("\"startCharOffset\":").append(numberMember(d, "startCharOffset")).append(',');
            json.append("\"endCharOffset\":").append(numberMember(d, "endCharOffset")).append(',');
            json.append("\"startLine\":").append(numberMember(d, "startLine")).append(',');
            json.append("\"startColumn\":").append(numberMember(d, "startColumn")).append(',');
            json.append("\"endLine\":").append(numberMember(d, "endLine")).append(',');
            json.append("\"endColumn\":").append(numberMember(d, "endColumn"));
            json.append('}');
        }
        json.append("]}");
        return json.toString();
    }

    /** Reads a string member, yielding the empty string when it is absent or not a string. */
    private static String stringMember(Value value, String member) {
        Value memberValue = value.getMember(member);
        return memberValue != null && memberValue.isString() ? memberValue.asString() : "";
    }

    /** Reads a long member, yielding zero when it is absent or does not fit in a long. */
    private static long numberMember(Value value, String member) {
        Value memberValue = value.getMember(member);
        return memberValue != null && memberValue.fitsInLong() ? memberValue.asLong() : 0L;
    }

    /** Escapes a value as a JSON string literal using the JSON string grammar. */
    public static String jsonString(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2);
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
        return out.toString();
    }
}
