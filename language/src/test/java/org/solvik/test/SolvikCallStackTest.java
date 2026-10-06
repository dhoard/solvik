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
     * `divide`'s division is line 2, `raiser`'s throw is line 6, the anonymous body inside `makeAdder`
     * divides at line 11, the closure inside `makeThrower` throws at line 17, and `Box.scaled` divides at
     * line 25.
     */
    private static final String LIBRARY = """
            func divide(numerator: Integer, denominator: Integer): Integer {
                return numerator / denominator
            }

            func raiser(value: Integer): Integer {
                throw Boom("from named")
            }

            func makeAdder(seed: Integer): func(Integer): Integer {
                return func [seed](value: Integer): Integer {
                    return seed / value
                }
            }

            func makeThrower(seed: Integer): func(Integer): Integer {
                return func [seed](value: Integer): Integer {
                    throw Boom("from closure " .. seed.toString())
                }
            }

            class Box {
                var tag: Integer = 8

                func scaled(value: Integer): Integer {
                    return this.tag / value
                }

                func shout(value: Integer): Integer {
                    throw Boom("from method")
                }
            }

            func makeBound(): func(Integer): Integer {
                return Box().scaled
            }

            func makeBoundShout(): func(Integer): Integer {
                return Box().shout
            }

            class Boom extends RuntimeException {
            }
            """;

    /**
     * The calling code, in the file that is evaluated. The lines the assertions name: `viaValue` performs
     * its indirect call at line 4 and `apply` at line 8. A tail written after this fixture therefore
     * begins at line 19.
     */
    private static final String PREAMBLE = """
            include "lib.sol"

            func viaValue(operation: func(Integer, Integer): Integer): Integer {
                return operation(4, 0)
            }

            func apply(operation: func(Integer): Integer): Integer {
                return operation(0)
            }

            func guarded(operation: func(Integer): Integer): String {
                try {
                    return "returned " .. operation(0).toString()
                }
                catch (error: Boom) {
                    return "caught " .. error.getMessage()
                }
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
    // The call a tool can identify
    // ---------------------------------------------------------------------------------------------

    /**
     * Calling a named function through a value adds no frame of its own: the callee appears directly above
     * the caller that invoked the value, under the callee's own name, and every frame names the physical
     * file holding the code it ran.
     *
     * <p>"Indirect calls ... appear as ordinary stack frames between the caller and callee" (section 6),
     * and a tool "can identify the call site node, the evaluated callable, and the call it performed"
     * (docs/ARCHITECTURE.md).
     */
    @Test
    public void anIndirectCallAppearsAsAnOrdinaryFrameBetweenCallerAndCallee() {
        PolyglotException failure = failing(program("println(viaValue(divide))\n"));
        assertThat(failure.getMessage()).contains("arithmetic error: division by zero");
        assertThat(failure.isGuestException()).isTrue();
        assertThat(failure.isInternalError()).as("a checked arithmetic fault is not a VM crash").isFalse();

        List<PolyglotException.StackFrame> frames = guestFrames(program("println(viaValue(divide))\n"));
        assertThat(rootsOf(frames)).containsExactly("divide", "viaValue", "main", "<eval>");

        // The callee frame is `divide`'s own division, in the included file, on its line 2.
        PolyglotException.StackFrame callee = frames.get(0);
        assertThat(callee.getRootName()).isEqualTo("divide");
        assertThat(fileOf(callee)).isEqualTo("lib.sol");
        assertThat(callee.getSourceLocation().getStartLine()).isEqualTo(2);
        assertThat(textOf(callee)).isEqualTo("numerator / denominator");

        // The caller frame names the evaluated file and covers the call expression that performed the
        // indirect call, inside `viaValue` on its line 4. That expression together with the callee frame
        // above it is what makes the call identifiable with no tag to select on.
        PolyglotException.StackFrame caller = frames.get(1);
        assertThat(caller.getRootName()).isEqualTo("viaValue");
        assertThat(fileOf(caller)).isEqualTo("root.sol");
        assertThat(caller.getSourceLocation().getStartLine()).isEqualTo(4);
        assertThat(textOf(caller)).isEqualTo("operation(4, 0)");
    }

    /**
     * A direct call and an indirect call report the same frame for the same callee: the same called-root
     * name, file, line, column, and section text. The stacks differ only by the extra Solvik frame the
     * indirect program has because it routes through another caller.
     *
     * <p>"An indirect call is instrumented the same way as a direct call" (docs/ARCHITECTURE.md). With no
     * node tags to compare today, that is the observable form of the claim: nothing in the callee's frame
     * tells a tool which form reached it.
     */
    @Test
    public void aDirectCallAndAnIndirectCallReportTheSameFrameForTheSameCallee() {
        List<PolyglotException.StackFrame> direct = guestFrames("include \"lib.sol\"\n\nprintln(divide(4, 0))\n");
        List<PolyglotException.StackFrame> indirect = guestFrames(program("println(viaValue(divide))\n"));

        assertThat(rootsOf(direct)).containsExactly("divide", "main", "<eval>");
        assertThat(rootsOf(indirect)).containsExactly("divide", "viaValue", "main", "<eval>");

        PolyglotException.StackFrame directCallee = direct.get(0);
        PolyglotException.StackFrame indirectCallee = indirect.get(0);
        assertThat(indirectCallee.getRootName()).isEqualTo(directCallee.getRootName());
        assertThat(fileOf(indirectCallee)).isEqualTo(fileOf(directCallee)).isEqualTo("lib.sol");
        assertThat(indirectCallee.getSourceLocation().getStartLine()).isEqualTo(directCallee.getSourceLocation().getStartLine());
        assertThat(indirectCallee.getSourceLocation().getStartColumn()).isEqualTo(directCallee.getSourceLocation().getStartColumn());
        assertThat(indirectCallee.getSourceLocation().getCharLength()).isEqualTo(directCallee.getSourceLocation().getCharLength());
        assertThat(textOf(indirectCallee)).isEqualTo(textOf(directCallee)).isEqualTo("numerator / denominator");
    }

    // ---------------------------------------------------------------------------------------------
    // The callable reached, for each kind of value
    // ---------------------------------------------------------------------------------------------

    /**
     * A capturing closure reports the anonymous root name together with a section of the file that holds
     * the anonymous expression, and its captured value contributes no frame: the stack runs from the
     * callee straight to the caller that invoked the value.
     *
     * <p>"An anonymous root carries a source section derived from its own expression, so a stack trace
     * names the physical file and the anonymous site" (docs/ARCHITECTURE.md); "a capturing closure ...
     * allocates no wrapper callable and adds no caller-observable frame" (section 6).
     */
    @Test
    public void aCapturingClosureReportsTheAnonymousNameAndTheFileHoldingItsOwnExpression() {
        List<PolyglotException.StackFrame> frames = guestFrames(program("println(apply(makeAdder(4)))\n"));
        assertThat(rootsOf(frames)).containsExactly("<anonymous>", "apply", "main", "<eval>");

        PolyglotException.StackFrame anonymous = frames.get(0);
        assertThat(anonymous.getRootName()).isEqualTo("<anonymous>");
        // The expression is written inside `makeAdder`, which the included file declares, so the frame
        // names that file even though the call happened in the evaluated one; the division it faulted on
        // is the body's line 11.
        assertThat(fileOf(anonymous)).isEqualTo("lib.sol");
        assertThat(anonymous.getSourceLocation().getStartLine()).isEqualTo(11);
        assertThat(textOf(anonymous)).isEqualTo("seed / value");

        PolyglotException.StackFrame caller = frames.get(1);
        assertThat(fileOf(caller)).isEqualTo("root.sol");
        assertThat(caller.getSourceLocation().getStartLine()).isEqualTo(8);
        assertThat(textOf(caller)).isEqualTo("operation(0)");
    }

    /**
     * An anonymous function written in the evaluated file names that file instead, which is the same rule
     * read from the other side: the section comes from the anonymous expression's own physical file, never
     * from the callable's origin or the caller.
     *
     * <p>"a stack trace names the physical file and the anonymous site" (docs/ARCHITECTURE.md).
     */
    @Test
    public void anAnonymousFunctionWrittenInTheEvaluatedFileNamesThatFile() {
        // The tail begins at line 19 of the composed program, so the anonymous body's division is line 20.
        List<PolyglotException.StackFrame> frames = guestFrames(program("""
                var half: func(Integer): Integer = func (value: Integer): Integer {
                    return 100 / value
                }

                println(apply(half))
                """));
        assertThat(rootsOf(frames)).containsExactly("<anonymous>", "apply", "main", "<eval>");

        PolyglotException.StackFrame anonymous = frames.get(0);
        assertThat(anonymous.getRootName()).isEqualTo("<anonymous>");
        assertThat(fileOf(anonymous)).isEqualTo("root.sol");
        assertThat(anonymous.getSourceLocation().getStartLine()).isEqualTo(20);
        assertThat(textOf(anonymous)).isEqualTo("100 / value");
    }

    /**
     * A bound method reference reports the method's own frame: the captured receiver is an argument, not a
     * frame, and nothing in the stack names the expression that produced the value or the function that
     * returned it.
     *
     * <p>"A safe bound reference invokes the method's own target" and "a safe bound method reference ...
     * does not allocate a wrapper callable or add a frame" (section 8, section 6). `makeBound` has already
     * returned when the call runs, so only the method and the caller that invoked the value are on the
     * stack.
     */
    @Test
    public void aBoundMethodReferenceReportsTheMethodsOwnFrame() {
        List<PolyglotException.StackFrame> frames = guestFrames(program("println(apply(makeBound()))\n"));
        assertThat(rootsOf(frames)).containsExactly("scaled", "apply", "main", "<eval>");

        PolyglotException.StackFrame method = frames.get(0);
        assertThat(method.getRootName()).isEqualTo("scaled");
        assertThat(fileOf(method)).isEqualTo("lib.sol");
        assertThat(method.getSourceLocation().getStartLine()).isEqualTo(25);
        assertThat(textOf(method)).isEqualTo("this.tag / value");
    }

    // ---------------------------------------------------------------------------------------------
    // Propagation across the frames a value reached
    // ---------------------------------------------------------------------------------------------

    /**
     * A value thrown inside a callable reached through a function value unwinds to a handler in an
     * enclosing guest frame for every kind of value: a declared function, a capturing closure, and a bound
     * method. The handler is three frames above the throw site and in the file that made the call.
     *
     * <p>"A `throw` inside a called function is caught by a handler in any dynamically enclosing function
     * frame, including the caller and its ancestors. A call target for an ordinary function does not itself
     * terminate the guest program" (section 22.4) — including a call target reached through a value.
     */
    @Test
    public void aThrownValueUnwindsThroughAValueCallToAnEnclosingGuestHandler() {
        assertThat(outputOf(program("println(guarded(raiser))\n"))).isEqualTo("caught from named\n");
        assertThat(outputOf(program("println(guarded(makeThrower(2)))\n"))).isEqualTo("caught from closure 2\n");
        assertThat(outputOf(program("println(guarded(makeBoundShout()))\n"))).isEqualTo("caught from method\n");
    }

    /**
     * The same throw with no handler left reaches the host as an ordinary guest failure naming the thrown
     * class and its message, never as a host internal error, and its outermost guest frame is the
     * evaluated source's boundary.
     *
     * <p>"An uncaught thrown value is a guest-visible failure ... and it is reported as an ordinary guest
     * error, never as a host internal error" (section 22.5). The frames between the throw site and that
     * boundary are not carried into the report today — a gap recorded in
     * docs/FIRST-CLASS-FUNCTIONS-PLAN.md — so what is asserted here is what section 22.5 fixes: the
     * reported class, the message, the failure kind, and the boundary frame.
     */
    @Test
    public void anUncaughtThrowFromAClosureIsReportedAsAnOrdinaryGuestFailure() {
        PolyglotException failure = failing(program("println(apply(makeThrower(1)))\n"));
        assertThat(failure.isGuestException()).as("section 22.5: an ordinary guest error").isTrue();
        assertThat(failure.isInternalError()).as("section 22.5: never a host internal error").isFalse();
        assertThat(failure.getMessage()).contains("uncaught guest exception of class 'Boom' with message 'from closure 1'");
        assertThat(run(program("println(apply(makeThrower(1)))\n")).guestCategory()).isEqualTo("UNCAUGHT_EXCEPTION");
        assertThat(rootsOf(guestFrames(failure))).endsWith("<eval>");
    }

    /**
     * A thrown value is a guest value that any enclosing frame may catch, while a language runtime fault is
     * not a guest value at all: a handler written on an exception base does not intercept the arithmetic
     * error a division raises, on the path a value opens or anywhere else.
     *
     * <p>"Integral division truncates toward zero and division by zero raises a Solvik runtime arithmetic
     * error" (section 3), and the catchable types are the nominal guest exception hierarchy of section
     * 22.1, which a runtime fault is not a member of. Both halves run the same program shape so the
     * difference cannot be attributed to the call route.
     */
    @Test
    public void aGuestHandlerCatchesThrownValuesAndNotALanguageRuntimeFault() {
        assertThat(outputOf(program("println(guarded(makeBoundShout()))\n"))).isEqualTo("caught from method\n");

        String handlerAboveTheFault = program("""
                try {
                    apply(makeBound())
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
        assertThat(rootsOf(guestFrames(result.failure()))).containsExactly("scaled", "apply", "main", "<eval>");
    }
}
