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
package org.solvik.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oracle.truffle.api.interop.ExceptionType;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.LibraryFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.solvik.lowering.LoweredProgram;
import org.solvik.lowering.SolvikLowering;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.source.SourceFile;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import org.solvik.parser.SolvikParseResult;
import org.solvik.source.SourceFile;
import org.solvik.truffle.SolvikException;
import org.solvik.truffle.object.SolvikAny;
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.SolvikParseException;
import org.solvik.truffle.SolvikUnit;
import org.solvik.truffle.nodes.SolvikGuestException;
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikAny;

/**
 * GraalVM interop validation: the Solvik runtime values and
 * compile errors expose the Truffle interop messages the polyglot and tooling layers rely on. The
 * direct {@link InteropLibrary} assertions exercise the same exported messages a host language
 * sees, and the polyglot assertions exercise the end-to-end {@code Value}/exception view.
 */
public final class SolvikInteropTest {

    private static final InteropLibrary INTEROP = LibraryFactory.resolve(InteropLibrary.class).getUncached();

    @Test
    public void unitIsANullLikeValueAndDisplaysAsUnit() throws Exception {
        assertThat(INTEROP.isNull(SolvikUnit.INSTANCE)).isTrue();
        assertThat(INTEROP.toDisplayString(SolvikUnit.INSTANCE)).isEqualTo("Unit");
    }

    @Test
    public void solvikAnyExposesItsClassNameToInterop() throws Exception {
        SolvikClass userClass = new SolvikClass("User", List.of("name"), List.of(false));
        SolvikAny user = new SolvikAny(userClass);
        assertThat(INTEROP.isNull(user)).isFalse();
        assertThat(INTEROP.toDisplayString(user)).isEqualTo("User");
    }

    @Test
    public void parseExceptionExposesParseErrorTypeAndSourceLocation() throws Exception {
        String text = "    var x: Integer = \"nope\"\n";
        com.oracle.truffle.api.source.Source source = com.oracle.truffle.api.source.Source.newBuilder("solvik", text, "bad.sol").build();
        SourceFile file = new SourceFile("bad.sol", text);
        SolvikParseException failure = SolvikParseException.create(source, file, org.solvik.parser.SolvikParser.parse(file).diagnostics());
        assertThat(INTEROP.getExceptionType(failure)).isEqualTo(ExceptionType.PARSE_ERROR);
        assertThat(INTEROP.hasSourceLocation(failure)).isTrue();
        assertThat(INTEROP.getSourceLocation(failure)).isNotNull();
    }

    @Test
    public void evaluatedEntryPointIsAUnitValueToPolyglot() {
        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            Value result = context.eval(source("    println(1)\n", "unit.sol"));
            assertThat(result.isNull()).as("an evaluated Solvik source yields Unit").isTrue();
        }
    }

    @Test
    public void polyglotExposesSolvikCompileErrorsAsLocatedSyntaxErrors() {
        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            try {
                context.eval(source("    var x: Integer = \"nope\"\n", "bad.sol"));
                throw new AssertionError("ill-typed source must be rejected");
            } catch (PolyglotException e) {
                assertThat(e.isSyntaxError()).isTrue();
                assertThat(e.getSourceLocation()).isNotNull();
                assertThat(e.getMessage().contains("SOLV-TYPE-001")).as(e.getMessage()).isTrue();
            }
        }
    }

    @Test
    public void parseExceptionExposesStructuredDiagnosticsThroughInterop() throws Exception {
        // A parser-level error so the parse stage alone produces a diagnostic (type errors require the
        // semantic analyzer and are not present on SolvikParser.parse alone).
        String text = "var x: Integer = = 5\n";
        com.oracle.truffle.api.source.Source source = com.oracle.truffle.api.source.Source.newBuilder("solvik", text, "bad.sol").build();
        SourceFile file = new SourceFile("bad.sol", text);
        SolvikParseException failure = SolvikParseException.create(source, file, org.solvik.parser.SolvikParser.parse(file).diagnostics());

        assertThat(INTEROP.hasMembers(failure)).isTrue();
        Object names = INTEROP.getMembers(failure, false);
        assertThat(INTEROP.getArraySize(names)).isEqualTo(1L);
        assertThat(INTEROP.asString(INTEROP.readArrayElement(names, 0))).isEqualTo("diagnostics");
        assertThat(INTEROP.isMemberReadable(failure, "diagnostics")).isTrue();
        assertThat(INTEROP.isMemberReadable(failure, "other")).isFalse();

        Object diagnostics = INTEROP.readMember(failure, "diagnostics");
        assertThat(INTEROP.hasArrayElements(diagnostics)).isTrue();
        assertThat(INTEROP.getArraySize(diagnostics)).isGreaterThanOrEqualTo(1L);

        Object diagnostic = INTEROP.readArrayElement(diagnostics, 0);
        assertThat(INTEROP.readMember(diagnostic, "family")).isEqualTo("PARS");
        assertThat(INTEROP.readMember(diagnostic, "code")).isEqualTo("SOLV-PARS-001");
        assertThat(INTEROP.readMember(diagnostic, "file")).isEqualTo("bad.sol");
        assertThat(INTEROP.asLong(INTEROP.readMember(diagnostic, "startLine"))).isEqualTo(1L);
        assertThat(INTEROP.asLong(INTEROP.readMember(diagnostic, "endCharOffset"))).isGreaterThan(0L);
    }

    @Test
    public void diagnosticInteropRejectsUnknownMemberAndIndex() throws Exception {
        String text = "var x: Integer = = 5\n";
        com.oracle.truffle.api.source.Source source = com.oracle.truffle.api.source.Source.newBuilder("solvik", text, "bad.sol").build();
        SourceFile file = new SourceFile("bad.sol", text);
        SolvikParseException failure = SolvikParseException.create(source, file, org.solvik.parser.SolvikParser.parse(file).diagnostics());

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedMessageException.class, () -> INTEROP.readMember(failure, "nope"));

        Object diagnostics = INTEROP.readMember(failure, "diagnostics");
        org.junit.jupiter.api.Assertions.assertThrows(InvalidArrayIndexException.class, () -> INTEROP.readArrayElement(diagnostics, 99));
        org.junit.jupiter.api.Assertions.assertThrows(InvalidArrayIndexException.class, () -> INTEROP.readArrayElement(diagnostics, -1));

        Object diagnostic = INTEROP.readArrayElement(diagnostics, 0);
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedMessageException.class, () -> INTEROP.readMember(diagnostic, "missing"));
        // The member-name array of a diagnostic is itself a bounded string array.
        Object memberNames = INTEROP.getMembers(diagnostic, false);
        org.junit.jupiter.api.Assertions.assertThrows(InvalidArrayIndexException.class, () -> INTEROP.readArrayElement(memberNames, 1000));
    }

    @Test
    public void structuredDiagnosticsCarryEveryFamilyAndOffsetThroughPolyglot() {
        record Expectation(String source, String family, String code) {
        }
        List<Expectation> cases = new ArrayList<>();
        cases.add(new Expectation("var s = \"unterminated\n", "LEX", "SOLV-LEX-001"));
        cases.add(new Expectation("var x: Integer = = 5\n", "PARS", "SOLV-PARS-001"));
        cases.add(new Expectation("var x: Integer = \"s\"\n", "TYPE", "SOLV-TYPE-001"));
        cases.add(new Expectation("undefinedName()\n", "RESOL", "SOLV-RESOL-001"));

        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            for (Expectation expectation : cases) {
                try {
                    context.eval(source(expectation.source(), "families.sol"));
                    throw new AssertionError("expected a compile error for: " + expectation.source());
                } catch (PolyglotException e) {
                    Value guest = e.getGuestObject();
                    assertThat(guest).isNotNull();
                    assertThat(guest.hasMembers()).isTrue();
                    Value diagnostics = guest.getMember("diagnostics");
                    assertThat(diagnostics.hasArrayElements()).isTrue();
                    boolean matched = false;
                    for (long i = 0; i < diagnostics.getArraySize(); i++) {
                        Value d = diagnostics.getArrayElement(i);
                        String family = d.getMember("family").asString();
                        String code = d.getMember("code").asString();
                        if (family.equals(expectation.family()) && code.equals(expectation.code())) {
                            matched = true;
                        }
                        assertThat(d.getMember("code").asString()).matches("^SOLV-[A-Z]+-[0-9]{2,4}$");
                        assertThat(d.getMember("startCharOffset").fitsInLong()).isTrue();
                        assertThat(d.getMember("endCharOffset").asLong()).isGreaterThanOrEqualTo(d.getMember("startCharOffset").asLong());
                    }
                    assertThat(matched).as("expected %s %s among diagnostics", expectation.family(), expectation.code()).isTrue();
                }
            }
        }
    }

    @Test
    public void parseExceptionWithNoDiagnosticsStillExposesEmptyDiagnosticsArray() throws Exception {
        String text = "println(1)\n";
        com.oracle.truffle.api.source.Source source = com.oracle.truffle.api.source.Source.newBuilder("solvik", text, "ok.sol").build();
        SourceFile file = new SourceFile("ok.sol", text);
        SolvikParseResult result = org.solvik.parser.SolvikParser.parse(file);
        assertThat(result.isSuccess()).isTrue();
        // A diagnostic bag with no entries still yields a valid, empty structured array.
        SolvikParseException empty = SolvikParseException.create(source, file, org.solvik.diagnostic.DiagnosticBag.builder().build());
        Object diagnostics = INTEROP.readMember(empty, "diagnostics");
        assertThat(INTEROP.getArraySize(diagnostics)).isEqualTo(0L);
    }

    @Test
    public void solvikExceptionExposesItsStableRuntimeCategoryToInterop() throws Exception {
        Object failure = SolvikException.arithmetic("division by zero", null);
        assertThat(INTEROP.hasMembers(failure)).isTrue();
        Object names = INTEROP.getMembers(failure, false);
        assertThat(INTEROP.getArraySize(names)).isEqualTo(1L);
        assertThat(INTEROP.asString(INTEROP.readArrayElement(names, 0))).isEqualTo("category");
        assertThat(INTEROP.isMemberReadable(failure, "category")).isTrue();
        assertThat(INTEROP.isMemberReadable(failure, "other")).isFalse();
        assertThat(INTEROP.asString(INTEROP.readMember(failure, "category"))).isEqualTo("ARITHMETIC_ERROR");
        assertThat(INTEROP.asString(INTEROP.readMember(SolvikException.typeError("bad cast", null), "category"))).isEqualTo("CAST_FAILURE");
        assertThat(INTEROP.asString(INTEROP.readMember(SolvikException.boundsError("index 9", null), "category"))).isEqualTo("INDEX_OUT_OF_BOUNDS");
        assertThat(INTEROP.asString(INTEROP.readMember(SolvikException.collectionError("missing key", null), "category"))).isEqualTo("COLLECTION_FAILURE");
        assertThat(INTEROP.asString(INTEROP.readMember(SolvikException.unknownCollectionMember("nope", null), "category"))).isEqualTo("COLLECTION_FAILURE");
        assertThat(INTEROP.asString(INTEROP.readMember(SolvikException.regexError("bad pattern", null), "category"))).isEqualTo("REGEX_FAILURE");
        assertThat(INTEROP.asString(INTEROP.readMember(SolvikException.unwrapFailed("unwrap", "Err", null), "category"))).isEqualTo("RESULT_WRONG_VARIANT");
        assertThat(INTEROP.asString(INTEROP.readMember(SolvikException.expectFailed("boom", "e", null), "category"))).isEqualTo("RESULT_WRONG_VARIANT");
        assertThat(INTEROP.asString(INTEROP.readMember(SolvikException.internalArity("f", 1, 2, null), "category"))).isEqualTo("OTHER_RUNTIME_ERROR");
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedMessageException.class, () -> INTEROP.readMember(failure, "missing"));
    }

    @Test
    public void polyglotGuestRuntimeExceptionCarriesStructuredCategoryMember() {
        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            try {
                context.eval(source("println(1/0)\n", "arith.sol"));
                throw new AssertionError("division by zero must fail at run time");
            } catch (PolyglotException e) {
                Value guest = e.getGuestObject();
                assertThat(guest).isNotNull();
                assertThat(guest.hasMembers()).isTrue();
                assertThat(guest.getMemberKeys()).contains("category");
                assertThat(guest.getMember("category").asString()).isEqualTo("ARITHMETIC_ERROR");
                assertThat(e.getSourceLocation()).isNotNull();
                assertThat(e.getSourceLocation().getCharIndex()).isEqualTo(8);
            }
        }
    }

    private static Source source(String text, String name) {
        try {
            return Source.newBuilder("solvik", text, name).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void anUncaughtGuestThrowReachesAHostAsTheBoundaryFailureRatherThanAsControlFlow() throws Exception {
        // Section 22.5 fixes what an uncaught throw reports: the thrown class with its message, as an
        // ordinary guest error, never a host internal error.
        String escaping = """
                class Boom extends RuntimeException {
                }

                throw Boom("nope")
                """;
        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            assertThatThrownBy(() -> context.eval(source(escaping, "escaping.sol")))//
                    .isInstanceOfSatisfying(PolyglotException.class, boundary -> {
                        assertThat(boundary.getMessage())//
                                .isEqualTo("uncaught guest exception of class 'Boom' with message 'nope'");
                        assertThat(boundary.isGuestException()).isTrue();
                        assertThat(boundary.isInternalError()).as("section 22.5: never a host internal error").isFalse();
                        assertThat(boundary.getGuestObject().getMember("category").asString()).isEqualTo("UNCAUGHT_EXCEPTION");
                    });
        }
    }
}
