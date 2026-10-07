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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.junit.jupiter.api.Test;

/**
 * What a tool sees when a program calls through a function value.
 *
 * <p>"An indirect call is instrumented the same way as a direct call: a tool can identify the call site
 * node, the evaluated callable, and the call it performed. Indirect calls carry the same call
 * instrumentation tags as direct calls", and "An anonymous root carries a source section derived from its
 * own expression, so a stack trace names the physical file and the anonymous site"
 * (docs/ARCHITECTURE.md, function-value architecture). Solvik nodes carry no instrumentation tags today —
 * a source-section or execution-event query attached to a Solvik program observes nothing at all, which
 * {@link SolvikInstrumentationTest} pins as the current state of the language — so the per-call
 * information a tool can be shown today is the guest call stack that a failure raised inside a callable
 * reports: one frame per call, each naming the called root and carrying a source section belonging to the
 * physical file that holds the code. These tests read that stack through {@code
 * PolyglotException.getPolyglotStackTrace()}, the structured form of the report a host receives.
 *
 * <p>Every program runs the way an end user runs it: {@code Context.eval} of a file that includes a second
 * file, so the frames name real physical files and the called code lives in the included one. Expected
 * names, files, lines, and section text are read off the two fixtures below by counting their lines; the
 * counted line is stated in the assertion comment.
 */
public final class SolvikCallStackTest {

    /**
     * The called code, in the file that is included rather than evaluated. The lines the assertions name:
     * `divide`'s division is line 2, `raiser`'s throw is line 6 and `Box.scaled` divides at line 13.
     */
    private static final String LIBRARY = """
            func divide(numerator: Integer, denominator: Integer): Integer {
                return numerator / denominator
            }

            func raiser(value: Integer): Integer {
                throw Boom("from named")
            }

            class Box {
                var tag: Integer = 8

                method scaled(value: Integer): Integer {
                    return this.tag / value
                }

                method shout(value: Integer): Integer {
                    throw Boom("from method")
                }
            }

            class Boom extends RuntimeException {
            }

            """;

    /**
     * The calling code, in the file that is evaluated. A tail written after this fixture therefore begins
     * at line 28.
     */
    private static final String PREAMBLE = """
            include "lib.sol"

            func guarded(): String {
                try {
                    return "returned " .. raiser(0).toString()
                }
                catch (error: Boom) {
                    return "caught " .. error.getMessage()
                }
            }

            func guardedMethod(): String {
                try {
                    return "returned " .. Box().shout(0).toString()
                }
                catch (error: Boom) {
                    return "caught " .. error.getMessage()
                }
            }

            func directCall(): Integer {
                return divide(1, 0)
            }
            """;

    /**
     * What one program run produced: everything it printed, the failure it reported if any, and the
     * runtime category that failure names through interop. The category is read while the context that
     * produced the failure is still open, because reading a guest member requires one.
     */
    private record Run(String output, PolyglotException failure, String guestCategory) {
    }

    /** Runs a program written against the fixtures, capturing its output and any failure. */
    private static Run run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Path root;
        try {
            Path directory = Files.createTempDirectory("solvik-call-stack");
            Files.writeString(directory.resolve("lib.sol"), LIBRARY, StandardCharsets.UTF_8);
            root = directory.resolve("root.sol");
            Files.writeString(root, source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        PolyglotException failure = null;
        String category = null;
        try (Context context = Context.newBuilder("solvik").out(out).err(out)
                .option("engine.WarnInterpreterOnly", "false").allowAllAccess(true).build()) {
            try {
                context.eval(Source.newBuilder("solvik", root.toFile()).build());
            } catch (PolyglotException reported) {
                failure = reported;
                category = reportedCategory(reported);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Run(out.toString(StandardCharsets.UTF_8), failure, category);
    }

    /**
     * The runtime category a reported guest failure names through its single interop member, or {@code
     * null} when the failure carries no guest object or no such member. Read inside the failing context.
     */
    private static String reportedCategory(PolyglotException failure) {
        org.graalvm.polyglot.Value guest = failure.getGuestObject();
        if (guest == null || !guest.getMemberKeys().contains("category")) {
            return null;
        }
        return guest.getMember("category").asString();
    }

    /** The failure a program reports, with the program printed into the message if it did not fail. */
    private static PolyglotException failing(String source) {
        Run run = run(source);
        assertThat(run.failure()).as("the program was expected to fail; it printed:\n%s", run.output()).isNotNull();
        return run.failure();
    }

    /** What a program that must succeed printed. */
    private static String outputOf(String source) {
        Run run = run(source);
        assertThat(run.failure()).as("the program was expected to succeed: %s", run.failure()).isNull();
        return run.output();
    }

    /** The guest frames of the failure a program reports, innermost first, host frames dropped. */
    private static List<PolyglotException.StackFrame> guestFrames(String source) {
        return guestFrames(failing(source));
    }

    /**
     * The guest frames of one failure, innermost first. Host frames are dropped: they belong to whatever
     * embedded the program, and the claims under test are about the Solvik frames a call produces.
     */
    private static List<PolyglotException.StackFrame> guestFrames(PolyglotException failure) {
        List<PolyglotException.StackFrame> frames = new ArrayList<>();
        for (PolyglotException.StackFrame frame : failure.getPolyglotStackTrace()) {
            if (frame.isGuestFrame()) {
                frames.add(frame);
            }
        }
        return frames;
    }

    private static String program(String tail) {
        return PREAMBLE + tail;
    }

    /** The called root name of every guest frame, in reported order. */
    private static List<String> rootsOf(List<PolyglotException.StackFrame> frames) {
        return frames.stream().map(PolyglotException.StackFrame::getRootName).toList();
    }

    /** The name of the file a frame's section belongs to, or {@code null} when it has no section. */
    private static String fileOf(PolyglotException.StackFrame frame) {
        SourceSection section = frame.getSourceLocation();
        return section == null ? null : section.getSource().getName();
    }

    /** The source text a frame's section covers, or {@code null} when it has no section. */
    private static String textOf(PolyglotException.StackFrame frame) {
        SourceSection section = frame.getSourceLocation();
        return section == null ? null : section.getCode().toString();
    }

    // ---------------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------------
    // The call a tool can identify
    // ---------------------------------------------------------------------------------------------

    /**
     * A direct call reports the callee's own frame directly above the caller: the callee's name, the
     * physical file holding the code it ran, and the source text of the faulting expression.
     *
     * <p>A tool "can identify the call site node, the evaluated callable, and the call it performed"
     * (docs/ARCHITECTURE.md), and every frame names the physical file holding the code it ran.
     */
    @Test
    public void aDirectCallReportsTheCalleesOwnFrameInTheIncludedFile() {
        List<PolyglotException.StackFrame> frames = guestFrames(program("directCall()\n"));
        assertThat(rootsOf(frames)).containsExactly("divide", "directCall", "main", "<eval>");

        PolyglotException.StackFrame callee = frames.get(0);
        assertThat(callee.getRootName()).isEqualTo("divide");
        assertThat(fileOf(callee)).isEqualTo("lib.sol");
        assertThat(callee.getSourceLocation().getStartLine()).isEqualTo(2);
        assertThat(textOf(callee)).isEqualTo("numerator / denominator");

        // The chain is what identifies the call: the callee's own frame, then the function that made
        // the call and the file's entry point above it.
        assertThat(frames.get(1).getRootName()).isEqualTo("directCall");
        assertThat(frames.get(2).getRootName()).isEqualTo("main");
    }

    /**
     * A method call reports the method's frame the same way, and the section it names is the method body in
     * the included file rather than the receiver expression at the call site.
     */
    @Test
    public void aMethodCallReportsTheMethodsOwnFrameInTheIncludedFile() {
        List<PolyglotException.StackFrame> frames = guestFrames(program("println(Box().scaled(0))\n"));
        assertThat(rootsOf(frames)).containsExactly("scaled", "main", "<eval>");

        PolyglotException.StackFrame method = frames.get(0);
        assertThat(method.getRootName()).isEqualTo("scaled");
        assertThat(fileOf(method)).isEqualTo("lib.sol");
        assertThat(method.getSourceLocation().getStartLine()).isEqualTo(13);
        assertThat(textOf(method)).isEqualTo("this.tag / value");
    }

    // ---------------------------------------------------------------------------------------------
    // Thrown values at the boundary
    // ---------------------------------------------------------------------------------------------

    /**
     * A thrown value is a guest value that any enclosing frame may catch: a handler written on the thrown
     * class catches it whether the throw came from a function or from a method.
     */
    @Test
    public void aThrownValueIsCaughtByAnEnclosingGuestHandler() {
        assertThat(outputOf(program("println(guarded())\n"))).isEqualTo("caught from named\n");
        assertThat(outputOf(program("println(guardedMethod())\n"))).isEqualTo("caught from method\n");
    }

    /**
     * The same throw with no handler left reaches the host as an ordinary guest failure naming the thrown
     * class and its message, never as a host internal error, and its outermost guest frame is the
     * evaluated source's boundary.
     *
     * <p>"An uncaught thrown value is a guest-visible failure ... and it is reported as an ordinary guest
     * error, never as a host internal error" (section 22.5).
     */
    @Test
    public void anUncaughtThrowFromAMethodIsReportedAsAnOrdinaryGuestFailure() {
        PolyglotException failure = failing(program("println(Box().shout(1))\n"));
        assertThat(failure.isGuestException()).as("section 22.5: an ordinary guest error").isTrue();
        assertThat(failure.isInternalError()).as("section 22.5: never a host internal error").isFalse();
        assertThat(failure.getMessage()).contains("uncaught guest exception of class 'Boom' with message 'from method'");
        assertThat(run(program("println(Box().shout(1))\n")).guestCategory()).isEqualTo("UNCAUGHT_EXCEPTION");
        assertThat(rootsOf(guestFrames(failure))).endsWith("<eval>");
    }

    /**
     * A thrown value is a guest value that any enclosing frame may catch, while a language runtime fault is
     * not a guest value at all: a handler written on an exception base does not intercept the arithmetic
     * error a division raises.
     *
     * <p>"Integral division truncates toward zero and division by zero raises a Solvik runtime arithmetic
     * error" (section 3), and the catchable types are the nominal guest exception hierarchy of section
     * 22.1, which a runtime fault is not a member of.
     */
    @Test
    public void aGuestHandlerCatchesThrownValuesAndNotALanguageRuntimeFault() {
        assertThat(outputOf(program("println(guardedMethod())\n"))).isEqualTo("caught from method\n");

        String handlerAboveTheFault = program("""
                try {
                    println(Box().scaled(0))
                }
                catch (error: RuntimeException) {
                    println("handler")
                }

                println("after")
                """);
        Run result = run(handlerAboveTheFault);
        assertThat(result.failure()).as("the fault must reach the boundary").isNotNull();
        assertThat(result.failure().getMessage()).contains("arithmetic error: division by zero");
        // Neither the handler nor the statement after the `try` ran, and the frames are the callee and its
        // caller as for any other fault.
        assertThat(result.output()).doesNotContain("handler").doesNotContain("after");
        assertThat(rootsOf(guestFrames(result.failure()))).containsExactly("scaled", "main", "<eval>");
    }
}
