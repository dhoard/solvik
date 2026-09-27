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

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.Value;

/**
 * Serializes the structured execute outcome produced by {@link SolvikMain#executeSource} into one
 * compact JSON line, so a machine-readable consumer (the TCK adapters) reads normal completion,
 * {@code exit(n)}, runtime failure, or a caught internal failure as fields of a closed vocabulary
 * rather than by parsing human-readable stderr text or relying only on the process exit code.
 *
 * <p>The vocabulary matches the execute-response {@code status} field of the TCK protocol:
 * {@code NORMAL_EXIT} carries a {@code languageExit}; {@code RUNTIME_FAILURE} carries a stable
 * {@code runtimeCategory} read through interop from the guest exception's {@code category} member plus
 * an optional half-open character-offset location and source file name; {@code IMPLEMENTATION_FAILURE}
 * marks a caught internal failure that must never satisfy a language-error or success expectation.
 * Character offsets are reported in the compiler's authoritative UTF-16 units; converting them to
 * UTF-8 byte offsets is the adapter's job, which owns the exact bytes of every staged file.
 */
public final class RunResultJson {

    /** The category reported when a guest runtime failure exposes no structured category member. */
    static final String OTHER = "OTHER_RUNTIME_ERROR";

    private RunResultJson() {
    }

    /** A normal completion, including an explicit {@code exit(n)}, with a language exit status. */
    public static String normalExit(int languageExit) {
        return "{\"phase\":\"execute\",\"status\":\"NORMAL_EXIT\",\"languageExit\":" + clampExit(languageExit) + "}";
    }

    /** A caught internal failure: a real IUT result, distinct from a language error. */
    public static String internalFailure() {
        return "{\"phase\":\"execute\",\"status\":\"IMPLEMENTATION_FAILURE\"}";
    }

    /**
     * A runtime failure classified through interop. The {@code runtimeCategory} is read from the guest
     * exception's structured {@code category} member; if the guest exposes no such member the outcome
     * falls back to {@code OTHER_RUNTIME_ERROR}, which is still a structured value rather than a
     * regexed message. A half-open character-offset span and the source file name are included when the
     * guest exception carries a known character location.
     */
    public static String runtimeFailure(PolyglotException ex, Source source) {
        Value guest = ex.getGuestObject();
        SourceSection section = ex.getSourceLocation();
        String file = (section != null && section.getSource() != null) ? section.getSource().getName() : source.getName();
        StringBuilder json = new StringBuilder();
        json.append('{');
        json.append("\"phase\":\"execute\",");
        json.append("\"status\":\"RUNTIME_FAILURE\",");
        json.append("\"runtimeCategory\":").append(DiagnosticsJson.jsonString(runtimeCategory(guest))).append(',');
        json.append("\"file\":").append(DiagnosticsJson.jsonString(file));
        if (section != null && section.hasCharIndex()) {
            long start = section.getCharIndex();
            long end = section.getCharLength() >= 0 ? section.getCharEndIndex() : start;
            json.append(",\"startCharOffset\":").append(start);
            json.append(",\"endCharOffset\":").append(Math.max(start, end));
        }
        json.append('}');
        return json.toString();
    }

    /**
     * Reads the stable runtime category from a guest exception value, falling back to the structured
     * {@link #OTHER} default when the guest exposes no readable {@code category} member. Taking the
     * guest {@link Value} directly (rather than the {@link PolyglotException}) keeps this pure and
     * unit-testable with host objects.
     */
    public static String runtimeCategory(Value guest) {
        if (guest != null && guest.hasMembers() && guest.getMemberKeys().contains("category")) {
            Value category = guest.getMember("category");
            if (category.isString()) {
                return category.asString();
            }
        }
        return OTHER;
    }

    /** Keeps a language exit status within the 0..255 process status range used by the protocol. */
    static int clampExit(int status) {
        if (status < 0) {
            return 0;
        }
        return status & 0xFF;
    }
}
