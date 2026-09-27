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
package org.solvik.launcher.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.solvik.launcher.DiagnosticsJson;

/**
 * Unit validation of the structured-diagnostics JSON serializer used by compile-only mode. Every JSON
 * string escape branch is exercised directly, and the {@code COMPILE_ERROR} envelope is checked against
 * real interop arrays whose elements expose the same member shape the guest diagnostic carries.
 */
public final class DiagnosticsJsonTest {

    /** A diagnostic-shaped host object; its public fields are exposed to interop as members. */
    public static final class Diag {
        public String family;
        public String code;
        public String text;
        public String file;
        public long startCharOffset;
        public long endCharOffset;
        public long startLine;
        public long startColumn;
        public long endLine;
        public long endColumn;

        Diag(String family, String code, String text, String file, long s, long e, long sl, long sc, long el, long ec) {
            this.family = family;
            this.code = code;
            this.text = text;
            this.file = file;
            this.startCharOffset = s;
            this.endCharOffset = e;
            this.startLine = sl;
            this.startColumn = sc;
            this.endLine = el;
            this.endColumn = ec;
        }
    }

    /** A diagnostic-shaped host object carrying only one field, to exercise the missing-member path. */
    public static final class PartialDiag {
        public String code;

        PartialDiag(String code) {
            this.code = code;
        }
    }

    /**
     * A diagnostic-shaped host object whose {@code family} and {@code startCharOffset} fields carry the
     * wrong type, so the serializer's present-but-not-a-string and present-but-not-a-long branches run.
     */
    public static final class WrongTypeDiag {
        public int family;
        public String startCharOffset;

        WrongTypeDiag() {
            this.family = 7;
            this.startCharOffset = "not-a-number";
        }
    }

    @Test
    public void jsonStringEscapesEveryControlAndStructuralCharacter() {
        assertThat(DiagnosticsJson.jsonString("a")).isEqualTo("\"a\"");
        assertThat(DiagnosticsJson.jsonString("quote\"back\\slash")).isEqualTo("\"quote\\\"back\\\\slash\"");
        assertThat(DiagnosticsJson.jsonString("nl\r\n\t")).isEqualTo("\"nl\\r\\n\\t\"");
        assertThat(DiagnosticsJson.jsonString("\b\f")).isEqualTo("\"\\b\\f\"");
        // A control character with no short escape is written as a backslash-u hex sequence.
        String control = "" + (char) 0 + (char) 1;
        assertThat(DiagnosticsJson.jsonString(control)).isEqualTo("\"" + "\\u0000" + "\\u0001" + "\"");
        // A non-ASCII printable character passes through unchanged.
        assertThat(DiagnosticsJson.jsonString("héllo")).isEqualTo("\"héllo\"");
    }

    @Test
    public void compileErrorEnvelopeCarriesStructuredGuestFields() {
        try (Context context = Context.newBuilder().allowAllAccess(true).build()) {
            Value diagnostics = context.asValue(List.of(new Diag("TYPE", "SOLV-TYPE-001", "initializer is not assignable to declared type Integer", "bad.sol", 17, 21, 1, 18, 1, 21)));
            String json = DiagnosticsJson.compileError("bad.sol", diagnostics);
            assertThat(json).startsWith("{\"phase\":\"compile\",\"status\":\"COMPILE_ERROR\",\"entryFile\":\"bad.sol\",\"diagnostics\":[");
            assertThat(json).contains("\"family\":\"TYPE\"");
            assertThat(json).contains("\"code\":\"SOLV-TYPE-001\"");
            assertThat(json).contains("\"startCharOffset\":17");
            assertThat(json).contains("\"endColumn\":21");
            assertThat(json).endsWith("]}");
        }
    }

    @Test
    public void missingMembersFallBackToEmptyStringAndZero() {
        try (Context context = Context.newBuilder().allowAllAccess(true).build()) {
            Value diagnostics = context.asValue(List.of(new PartialDiag("SOLV-SEM-045")));
            String json = DiagnosticsJson.compileError("x.sol", diagnostics);
            assertThat(json).contains("\"family\":\"\"");
            assertThat(json).contains("\"code\":\"SOLV-SEM-045\"");
            assertThat(json).contains("\"startCharOffset\":0");
            assertThat(json).contains("\"text\":\"\"");
        }
    }

    @Test
    public void presentButWronglyTypedMembersFallBack() {
        try (Context context = Context.newBuilder().allowAllAccess(true).build()) {
            Value diagnostics = context.asValue(List.of(new WrongTypeDiag()));
            String json = DiagnosticsJson.compileError("wt.sol", diagnostics);
            // family (an int member) and startCharOffset (a String member) are present but the wrong
            // kind, so both fall back rather than serializing the wrong-typed value.
            assertThat(json).contains("\"family\":\"\"");
            assertThat(json).contains("\"startCharOffset\":0");
        }
    }

    @Test
    public void emptyDiagnosticsArrayProducesAnEmptyArrayLiteral() {
        try (Context context = Context.newBuilder().allowAllAccess(true).build()) {
            Value diagnostics = context.asValue(List.of());
            String json = DiagnosticsJson.compileError("empty.sol", diagnostics);
            assertThat(json).isEqualTo("{\"phase\":\"compile\",\"status\":\"COMPILE_ERROR\",\"entryFile\":\"empty.sol\",\"diagnostics\":[]}");
        }
    }

    @Test
    public void multipleDiagnosticsAreCommaSeparated() {
        try (Context context = Context.newBuilder().allowAllAccess(true).build()) {
            Value diagnostics = context.asValue(List.of(
                    new Diag("LEX", "SOLV-LEX-001", "a", "m.sol", 0, 1, 1, 1, 1, 2),
                    new Diag("PARS", "SOLV-PARS-002", "b", "m.sol", 2, 3, 1, 3, 1, 4)));
            String json = DiagnosticsJson.compileError("m.sol", diagnostics);
            // Exactly one comma separates the two objects inside the array.
            assertThat(json).contains("},{");
            assertThat(json.indexOf("SOLV-LEX-001")).isGreaterThanOrEqualTo(0);
            assertThat(json.indexOf("SOLV-LEX-001")).isLessThan(json.indexOf("SOLV-PARS-002"));
        }
    }
}
