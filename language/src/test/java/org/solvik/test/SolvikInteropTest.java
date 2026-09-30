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
import org.solvik.truffle.SolvikException;
import org.solvik.truffle.SolvikFunction;
import org.solvik.truffle.SolvikParseException;
import org.solvik.truffle.SolvikUnit;
import org.solvik.truffle.nodes.SolvikGuestException;
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikFunctionValue;
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
        String text = "    val x: Integer = \"nope\"\n";
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
                context.eval(source("    val x: Integer = \"nope\"\n", "bad.sol"));
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
        String text = "val x: Integer = = 5\n";
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
        String text = "val x: Integer = = 5\n";
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
        cases.add(new Expectation("val s = \"unterminated\n", "LEX", "SOLV-LEX-001"));
        cases.add(new Expectation("val x: Integer = = 5\n", "PARS", "SOLV-PARS-001"));
        cases.add(new Expectation("val x: Integer = \"s\"\n", "TYPE", "SOLV-TYPE-001"));
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

    // ---------------------------------------------------------------------------------------------
    // Function values at the interoperation boundary (docs/LANGUAGE_SPEC.md section 6, "Type tests,
    // casts, and other constructs", and section 22.5, "Program boundary").
    //
    // "At the interoperation boundary a non-null function value reports itself as executable. Host
    // execution enforces the function's arity as an internal runtime invariant and invokes the same
    // call target as guest execution; guest source never relies on that runtime check, because semantic
    // analysis rejects a bad arity before execution. Function parameter and return type metadata need
    // not be reflectively exposed to hosts." (section 6)
    //
    // A Solvik program has no way to hand a function value to an embedding host: "Evaluating a Solvik
    // source file from an embedding host yields no value. The evaluated result of a file is the Unit
    // value" (section 22.5), so `Context.eval` never returns one and `Value.execute` therefore has no
    // Solvik-created function value to reach. The assertions below therefore run in two halves. The
    // capability layer that a host asks first is asked through a real `Context` and polyglot `Value`.
    // Invocation, arity, and exception behaviour are asserted through `InteropLibrary`, which is the
    // library `Value` delegates to and which applies no sharing check of its own. Both halves use values
    // and call targets that real lowering produced for a real program (`lowerFunctionValues` runs the
    // same parse, analysis, and lowering that {@code SolvikLanguage.parse} runs), so what a host reaches
    // is the same value object and the same target a guest program holds, not a stand-in.
    // ---------------------------------------------------------------------------------------------

    /**
     * One program covering every function-value kind: the canonical value of a declared function, a
     * capturing closure, and a bound method reference; results of each representation the interop rules
     * distinguish; and two throws, one handled inside the body and one left uncaught.
     */
    private static final String FUNCTION_VALUE_PROGRAM = """
            func triple(value: Integer): Integer {
                return value * 21
            }

            func label(value: Integer): String {
                return "v" .. value.toString()
            }

            func flag(value: Integer): Boolean {
                return value > 0
            }

            func quiet() {
                val unused: Integer = 1
            }

            func nothing(): (func(Integer): Integer)? {
                return null
            }

            class Boom extends RuntimeException {
            }

            func failing(): Integer {
                throw Boom("nope")
            }

            func guarded(): String {
                try {
                    return "returned " .. failing().toString()
                } catch (error: Boom) {
                    return "caught " .. error.getMessage()
                }
            }

            class Box {
                val tag: String = "box"

                func describe(value: Integer): String {
                    return this.tag .. value.toString()
                }
            }

            func makeClosure(seed: Integer): func(Integer): Integer {
                return func [seed](value: Integer): Integer {
                    return value + seed
                }
            }

            func makeBound(): func(Integer): String {
                return Box().describe
            }

            func makeAnonymous(): func(Integer): Integer {
                return func(value: Integer): Integer {
                    return value - 1
                }
            }

            func holder(): func(Integer): Integer {
                return triple
            }
            """;

    /**
     * Lowers {@link #FUNCTION_VALUE_PROGRAM} through the same parse, analysis, and lowering steps
     * {@code SolvikLanguage.parse} runs, without executing it. {@code analyze(CompilationUnitNode)} is the
     * analyzer entry point that treats a source as one standalone file with no includes, which is what
     * this program is. No {@code SolvikLanguage} instance is passed to lowering: lowering needs one only
     * to build root nodes, and these tests invoke function targets directly rather than evaluating a
     * source file's {@code main}, so nothing would read it.
     */
    private static LoweredProgram lowerFunctionValues() {
        String text = FUNCTION_VALUE_PROGRAM;
        SolvikParseResult parsed = SolvikParser.parse(new SourceFile("function-values.sol", text));
        assertThat(parsed.isSuccess()).as("parse: %s", parsed.diagnostics().all()).isTrue();
        SemanticResult analyzed = SolvikSemanticAnalyzer.analyze(parsed.requireAst());
        assertThat(analyzed.isSuccess()).as("analysis: %s", analyzed.diagnostics().all()).isTrue();
        com.oracle.truffle.api.source.Source source = com.oracle.truffle.api.source.Source//
                .newBuilder("solvik", text, "function-values.sol").build();
        return SolvikLowering.lower(analyzed.requireProgram(), Map.of(0, source), null);
    }

    private static SolvikFunction function(LoweredProgram program, String name) {
        return program.function(name).orElseThrow(() -> new AssertionError("no lowered function named " + name));
    }

    /** Invokes one lowered function as a guest caller would, to compare a host call against it. */
    private static Object call(SolvikFunction function, Object... arguments) {
        return function.callTarget().call(arguments);
    }

    /** Runs one factory function of {@link #FUNCTION_VALUE_PROGRAM} and hands back what it produced. */
    private static Object produced(LoweredProgram program, String name, Object... arguments) {
        return call(function(program, name), arguments);
    }

    private static String categoryOf(Object failure) throws Exception {
        return INTEROP.asString(INTEROP.readMember(failure, "category"));
    }

    /** Runs one host call that must fail and hands back the failure the host was given. */
    private static Throwable capturedFailure(org.assertj.core.api.ThrowableAssert.ThrowingCallable hostCall) {
        Throwable failure = org.assertj.core.api.Assertions.catchThrowable(hostCall);
        assertThat(failure).isInstanceOf(SolvikException.class);
        return failure;
    }

    @Test
    public void everyFunctionValueKindReportsExecutableCapabilityAndTheFixedDisplay() throws Exception {
        LoweredProgram program = lowerFunctionValues();
        Object named = function(program, "triple").functionValue();
        Object closure = produced(program, "makeClosure", 40);
        Object bound = produced(program, "makeBound");
        Object anonymous = produced(program, "makeAnonymous");

        assertThat(closure).isInstanceOf(SolvikFunctionValue.class);
        assertThat(bound).isInstanceOf(SolvikFunctionValue.class);
        assertThat(anonymous).isInstanceOf(SolvikFunctionValue.class);

        // "At the interoperation boundary a non-null function value reports itself as executable."
        // Every kind shares the one closed runtime shape, so each must answer alike.
        for (Object value : List.of(named, closure, bound, anonymous)) {
            assertThat(INTEROP.isExecutable(value)).as("executable %s", value).isTrue();
            // "print, println, and .. render every function value as `func`" — and a host sees the
            // guest rendering rather than a shape that could expose a target, a receiver, or a capture.
            assertThat(INTEROP.toDisplayString(value)).isEqualTo(SolvikFunctionValue.DISPLAY);
            assertThat(INTEROP.isNull(value)).isFalse();
            // No members, no array, no hash entries: section 6 gives a function value no observable
            // surface beyond executability and the fixed rendering, and no parameter or result metadata.
            assertThat(INTEROP.hasMembers(value)).isFalse();
            assertThat(INTEROP.hasArrayElements(value)).isFalse();
            assertThat(INTEROP.hasHashEntries(value)).isFalse();
            assertThat(INTEROP.fitsInLong(value)).isFalse();
            assertThatThrownBy(() -> INTEROP.readMember(value, "arity")).isInstanceOf(UnsupportedMessageException.class);
        }
    }

    @Test
    public void aPolyglotHostSeesAFunctionValueAsAnExecutableWithNoMembers() throws Exception {
        LoweredProgram program = lowerFunctionValues();
        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            context.eval(source("val warm: Integer = 1\n", "warm.sol"));
            Value host = context.asValue(function(program, "triple").functionValue());

            // "...a non-null function value reports itself as executable" — asked the way an embedding
            // host asks it, through the polyglot capability layer rather than the library directly.
            assertThat(host.canExecute()).isTrue();
            assertThat(host.isNull()).as("a function value is a non-null reference").isFalse();

            // Section 6 gives a function value no other observable surface: no members, and so no
            // parameter or return type metadata, and nothing array-, hash-, string-, or number-shaped.
            assertThat(host.hasMembers()).isFalse();
            assertThat(host.hasArrayElements()).isFalse();
            assertThat(host.hasHashEntries()).isFalse();
            assertThat(host.fitsInLong()).isFalse();
            assertThat(host.isHostObject()).as("a Solvik value is not a host object").isFalse();
            assertThat(host.getSourceLocation()).as("section 6 does not make a callable a source object").isNull();
        }
    }

    @Test
    public void hostExecutionOfANamedFunctionValueInvokesTheDeclarationsOwnTarget() throws Exception {
        LoweredProgram program = lowerFunctionValues();
        SolvikFunction triple = function(program, "triple");
        SolvikFunctionValue named = triple.functionValue();

        // "...invokes the same call target as guest execution" (section 6). The target a value carries is
        // the target the declaration's own direct call uses, so a host call and a guest call cannot run
        // different code, and it is the target a guest reaches through the dispatch node.
        assertThat(named.target()).isSameAs(triple.callTarget());
        assertThat(INTEROP.execute(named, 2)).isEqualTo(call(triple, 2)).isEqualTo(42);

        // The canonical value is the value: returning it from a function hands out the same object
        // rather than a copy, so a host that receives it from two places still holds one identity.
        assertThat(produced(program, "holder")).isSameAs(named);
        assertThat(triple.functionValue()).isSameAs(named);
    }

    @Test
    public void hostExecutionSuppliesTheHiddenReceiverAndCapturedArgumentsItself() throws Exception {
        LoweredProgram program = lowerFunctionValues();
        Object closure = produced(program, "makeClosure", 40);
        Object bound = produced(program, "makeBound");

        // A host supplies the arguments the Solvik source declares — the function type of a closure
        // excludes its captures and the function type of a bound method excludes its receiver
        // (section 6), and the value supplies what its type excludes.
        assertThat(INTEROP.execute(closure, 2)).isEqualTo(42);
        assertThat(INTEROP.execute(bound, 7)).isEqualTo("box7");

        // The assembly those two calls depend on, stated directly: the receiver first when there is
        // one, then the captured values in capture-list order, then the guest arguments untouched.
        SolvikFunctionValue boundValue = (SolvikFunctionValue) bound;
        assertThat(boundValue.hasReceiver()).isTrue();
        assertThat(SolvikFunctionValue.withCapturedState(boundValue, new Object[]{"arg"}))//
                .containsExactly(boundValue.receiver(), "arg");
        // A `[this]` capture is a captured value and not a receiver, so a closure has none to prepend
        // and contributes only its captured array.
        SolvikFunctionValue closureValue = (SolvikFunctionValue) closure;
        assertThat(closureValue.hasReceiver()).isFalse();
        assertThat(SolvikFunctionValue.withCapturedState(closureValue, new Object[]{2})).containsExactly(40, 2);
        // A value that leads with nothing hands the host array straight through.
        SolvikFunctionValue named = function(program, "triple").functionValue();
        assertThat(SolvikFunctionValue.withCapturedState(named, new Object[]{2})).containsExactly(2);
    }

    @Test
    public void hostExecutionResultsConvertThroughTheOrdinaryInteropRules() throws Exception {
        LoweredProgram program = lowerFunctionValues();

        Object integer = INTEROP.execute(function(program, "triple").functionValue(), 2);
        assertThat(INTEROP.fitsInLong(integer)).isTrue();
        assertThat(INTEROP.asLong(integer)).isEqualTo(42L);

        Object text = INTEROP.execute(function(program, "label").functionValue(), 7);
        assertThat(INTEROP.isString(text)).isTrue();
        assertThat(INTEROP.asString(text)).isEqualTo("v7");

        Object flag = INTEROP.execute(function(program, "flag").functionValue(), 1);
        assertThat(INTEROP.isBoolean(flag)).isTrue();
        assertThat(INTEROP.asBoolean(flag)).isTrue();

        // "Invocation returns the declared result", and an omitted return type declares `Unit`, which is
        // the same null-like value a host already sees at the end of an evaluated source file.
        Object unit = INTEROP.execute(function(program, "quiet").functionValue());
        assertThat(unit).isSameAs(SolvikUnit.INSTANCE);
        assertThat(INTEROP.isNull(unit)).isTrue();
    }

    /**
     * A nullable function value that holds no value reports no executability at all.
     *
     * <p>"At the interoperation boundary a *non-null* function value reports itself as executable"
     * (section 6): the conditional is on the value, and the half of it the executable assertions above
     * cannot reach needs its own witness. A Solvik null has no runtime object — the value a nullable
     * function-typed binding holds when it holds nothing is the absence of a receiver — so `InteropLibrary`
     * is never even dispatched, which is the strongest available form of "does not report itself
     * executable". The layer a host actually holds is the polyglot `Value`, and there the answer is stated
     * directly: null, and not executable, and an attempted call is unsupported rather than a failure.
     *
     * <p>This is the witness the boundary contract of requirement `REQ-3308` names, in the embedded-API
     * suite rather than the portable corpus, because a host holding a guest value is not something a guest
     * program can print or assert.
     */
    @Test
    public void aNullableFunctionValueHoldingNoValueDoesNotReportExecutable() throws Exception {
        Object empty = produced(lowerFunctionValues(), "nothing");
        assertThat(empty).as("a Solvik null is no object, so no library is dispatched for it").isNull();

        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            context.eval(source("val warm: Integer = 1\n", "warm.sol"));
            Value hostNull = context.asValue(empty);
            assertThat(hostNull.isNull()).isTrue();
            assertThat(hostNull.canExecute()).as("section 6 makes executability conditional on non-null").isFalse();
            assertThat(hostNull.hasMembers()).isFalse();
            // The attempted call is refused by the host API itself — "Unsupported operation Value.execute
            // (Object...) for 'null'" — rather than reaching a language as an unsupported message, because
            // there is no value to reach. `canExecute()` is stated as the guard the refusal points at.
            assertThatThrownBy(() -> hostNull.execute()).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test
    public void aHostCallWithTheWrongArgumentCountFailsTheInternalArityInvariant() throws Exception {
        LoweredProgram program = lowerFunctionValues();
        SolvikFunctionValue named = function(program, "triple").functionValue();
        Object closure = produced(program, "makeClosure", 40);

        // "Host execution enforces the function's arity as an internal runtime invariant ... guest
        // source never relies on that runtime check, because semantic analysis rejects a bad arity
        // before execution." So the failure is the internal invariant, named by its composed message,
        // and it fires in both directions of the mistake.
        assertThatThrownBy(() -> INTEROP.execute(named))//
                .isInstanceOf(SolvikException.class)//
                .hasMessage("internal error: callable 'triple' expected 1 frame argument(s) but execution supplied 0");
        assertThatThrownBy(() -> INTEROP.execute(named, 1, 2))//
                .isInstanceOf(SolvikException.class)//
                .hasMessage("internal error: callable 'triple' expected 1 frame argument(s) but execution supplied 2");

        // The category identifies an arity failure uniquely — no other producer uses it — and the same
        // invariant guards a value whose frame arity counts hidden arguments: the closure above declares
        // one parameter and captures one value, so supplying none is a mismatch of the same kind and
        // the value's own captured argument cannot make up the difference. That the mistake cannot be
        // written in Solvik source at all — {@code SolvikFunctionValueTest}
        // .anIndirectCallWithTheWrongArityIsRejected rejects the call site before lowering — is what
        // makes this an internal invariant rather than a diagnostic.
        Throwable tooMany = capturedFailure(() -> INTEROP.execute(named, 1, 2));
        assertThat(categoryOf(tooMany)).isEqualTo("OTHER_RUNTIME_ERROR");
        assertThatThrownBy(() -> INTEROP.execute(closure))//
                .isInstanceOf(SolvikException.class)//
                .hasMessageContaining("expected 2 frame argument(s) but execution supplied 1");
    }

    @Test
    public void anUncaughtGuestThrowReachesAHostAsTheBoundaryFailureRatherThanAsControlFlow() throws Exception {
        LoweredProgram program = lowerFunctionValues();
        SolvikFunctionValue failing = function(program, "failing").functionValue();
        Throwable escapingThrow = capturedFailure(() -> INTEROP.execute(failing));

        // Section 6: "Invocation returns the declared result and propagates guest exceptions without
        // wrapping or translation", and section 22.5 fixes what an uncaught throw reports: the thrown
        // class with its message, as an ordinary guest error, never a host internal error.
        assertThat(escapingThrow).isInstanceOf(SolvikException.class).hasMessage("uncaught guest exception of class 'Boom' with message 'nope'");
        // A guest throw travels between Solvik frames as a catchable control-flow signal, and a signal of
        // that kind must not reach a host: a host catches failures, not unwinding.
        assertThat(escapingThrow).isNotInstanceOf(SolvikGuestException.class);
        assertThat(categoryOf(escapingThrow)).isEqualTo("UNCAUGHT_EXCEPTION");

        // The same throw with no handler, reaching a host at the boundary a source file has, reports the
        // identical failure. A host call has no Solvik handler above it either, so a difference between
        // the two would mean one of them was translating the value.
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

        // A handler inside the invoked body still handles the throw when the call arrived from a host:
        // converting an escaping value at the boundary must not intercept anything below it.
        assertThat(INTEROP.execute(function(program, "guarded").functionValue())).isEqualTo("caught nope");
    }
}
