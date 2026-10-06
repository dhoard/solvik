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
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

/**
 * End-to-end execution tests for unchecked exceptions: {@code throw}, {@code try}, typed
 * {@code catch}, {@code finally}, runtime class dispatch, {@code finally} suppression and
 * rethrow, and the program-boundary conversion of an uncaught throw into a guest-visible failure
 * (docs/LANGUAGE_SPEC.md error-handling phases). Top-level executable statements form the entry
 * point; {@code err} is merged into the captured stream so the asserted output is exactly the
 * program's own writes, as observed through the shipped entry points.
 */
public final class SolvikExceptionExecutionTest {

    private static final class Result {
        final String output;
        final PolyglotException failure;

        Result(String output, PolyglotException failure) {
            this.output = output;
            this.failure = failure;
        }
    }

    private static Result evaluate(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotException failure = null;
        try (Context context = Context.newBuilder("solvik")
                .out(out)
                .err(out)
                .option("engine.WarnInterpreterOnly", "false")
                .allowAllAccess(true)
                .build()) {
            context.eval(build(source));
        } catch (PolyglotException e) {
            failure = e;
        }
        return new Result(out.toString(StandardCharsets.UTF_8), failure);
    }

    private static Source build(String source) {
        try {
            return Source.newBuilder("solvik", source, "exception.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void catchHandlesExactThrownClass() {
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "try {\n"
                        + "  throw AppError()\n"
                        + "}\n"
                        + "catch (e: AppError) {\n"
                        + "  println(\"caught-app-error\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("caught-app-error\n");
    }

    @Test
    public void baseClassCatchMatchesSubclassAtRuntime() {
        // A handler typed on a base class catches a thrown subclass, dispatched by the
        // handler's transitive matched set rather than the exact runtime class.
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "try {\n"
                        + "  throw AppError()\n"
                        + "}\n"
                        + "catch (e: RuntimeException) {\n"
                        + "  println(\"caught-runtime\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("caught-runtime\n");
    }

    @Test
    public void rootExceptionHandlerCatchesClassUnderABuiltinBase() {
        // The built-in bases have no source declaration, so the graph built from declarations alone
        // cannot see the RuntimeException -> Exception edge. Without that edge a chain that passes
        // through a built-in base is truncated and a handler on the root type catches nothing, even
        // though the handler matches whenever the thrown class's chain reaches it (section 22.3).
        // `baseClassCatchMatchesSubclassAtRuntime` passes either way because it stops one level below
        // a built-in base, which is why the two-level case is asserted separately.
        Result result = evaluate(
                "class ParseError extends RuntimeException {\n"
                        + "}\n"
                        + "try {\n"
                        + "  throw ParseError(\"bad int\")\n"
                        + "}\n"
                        + "catch (e: Exception) {\n"
                        + "  println(\"caught-root\")\n"
                        + "  println(e.getMessage())\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("caught-root\nbad int\n");
    }

    @Test
    public void rootExceptionHandlerCatchesClassUnderApplicationBase() {
        Result result = evaluate(
                "class ConfigError extends ApplicationException {\n"
                        + "}\n"
                        + "try {\n"
                        + "  throw ConfigError(\"no config\")\n"
                        + "}\n"
                        + "catch (e: Exception) {\n"
                        + "  println(\"caught-application-via-root\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("caught-application-via-root\n");
    }

    @Test
    public void rootExceptionHandlerCatchesATransitivelyDerivedClass() {
        Result result = evaluate(
                "mutable class ParseError extends RuntimeException {\n"
                        + "}\n"
                        + "class DeepError extends ParseError {\n"
                        + "}\n"
                        + "try {\n"
                        + "  throw DeepError(\"deep\")\n"
                        + "}\n"
                        + "catch (e: Exception) {\n"
                        + "  println(e.getMessage())\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("deep\n");
    }

    @Test
    public void aHandlerUnderABuiltinBaseAfterARootHandlerIsUnreachable() {
        // Unreachability is the same reachability question as matching, so it must agree with it:
        // RuntimeException reaches Exception, therefore the later clause can never run (section 22.3).
        Result result = evaluate(
                "try {\n"
                        + "  println(\"trying\")\n"
                        + "}\n"
                        + "catch (e: Exception) {\n"
                        + "  println(\"root\")\n"
                        + "}\n"
                        + "catch (e: RuntimeException) {\n"
                        + "  println(\"never\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.getMessage().contains("SOLV-SEM-055")).as(result.failure.getMessage()).isTrue();
    }

    @Test
    public void siblingBuiltinBasesDoNotMakeEachOtherUnreachable() {
        // RuntimeException and ApplicationException are siblings, not ancestor and descendant, so an
        // Exception-rooted graph must not order them: both clauses remain reachable.
        Result result = evaluate(
                "class AppError extends ApplicationException {\n"
                        + "}\n"
                        + "try {\n"
                        + "  throw AppError(\"app\")\n"
                        + "}\n"
                        + "catch (e: RuntimeException) {\n"
                        + "  println(\"runtime\")\n"
                        + "}\n"
                        + "catch (e: ApplicationException) {\n"
                        + "  println(\"application\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("application\n");
    }

    @Test
    public void firstMatchingHandlerWins() {
        // A non-matching handler is skipped and the next matching handler runs.
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "class OtherError extends RuntimeException {\n"
                        + "}\n"
                        + "try {\n"
                        + "  throw AppError()\n"
                        + "}\n"
                        + "catch (e: OtherError) {\n"
                        + "  println(\"wrong-handler\")\n"
                        + "}\n"
                        + "catch (e: AppError) {\n"
                        + "  println(\"right-handler\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("right-handler\n");
    }

    @Test
    public void exceptionInNestedCallIsCaughtInCaller() {
        // A throw in a called function must cross the call-target boundary (not be swallowed at
        // the callee root) so an enclosing try in the caller can handle it.
        Result result = evaluate(
                "class ParseError extends RuntimeException {\n"
                        + "}\n"
                        + "func parse(): Integer {\n"
                        + "  throw ParseError()\n"
                        + "}\n"
                        + "try {\n"
                        + "  parse()\n"
                        + "}\n"
                        + "catch (e: ParseError) {\n"
                        + "  println(\"caught-in-caller\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("caught-in-caller\n");
    }

    @Test
    public void finallyRunsAfterNormalCompletion() {
        Result result = evaluate(
                "try {\n"
                        + "  println(\"body\")\n"
                        + "}\n"
                        + "finally {\n"
                        + "  println(\"cleanup\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("body\ncleanup\n");
    }

    @Test
    public void finallyRunsOnExceptionalCompletion() {
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "try {\n"
                        + "  throw AppError()\n"
                        + "}\n"
                        + "catch (e: AppError) {\n"
                        + "  println(\"caught\")\n"
                        + "}\n"
                        + "finally {\n"
                        + "  println(\"cleanup\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("caught\ncleanup\n");
    }

    @Test
    public void finallyRunsBeforePropagationToOuterHandler() {
        // An inner finally runs while the exception propagates to an outer handler.
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "class FinallyError extends RuntimeException {\n"
                        + "}\n"
                        + "try {\n"
                        + "  try {\n"
                        + "    throw AppError()\n"
                        + "  }\n"
                        + "  finally {\n"
                        + "    println(\"inner-cleanup\")\n"
                        + "  }\n"
                        + "}\n"
                        + "catch (e: AppError) {\n"
                        + "  println(\"outer-caught\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("inner-cleanup\nouter-caught\n");
    }

    @Test
    public void finallyThrowReplacesCompletedHandler() {
        // When the finally block throws after a handler completes normally, the finally's
        // exception becomes the propagated one (suppression): the handler runs to completion and
        // its output is kept, but the finally's new exception escapes to the boundary.
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "class FinallyError extends RuntimeException {\n"
                        + "}\n"
                        + "try {\n"
                        + "  throw AppError()\n"
                        + "}\n"
                        + "catch (e: AppError) {\n"
                        + "  println(\"handler\")\n"
                        + "}\n"
                        + "finally {\n"
                        + "  throw FinallyError()\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.isInternalError()).as(result.failure.getMessage()).isFalse();
        assertThat(result.failure.getMessage().contains("FinallyError")).as(result.failure.getMessage()).isTrue();
        assertThat(result.output).isEqualTo("handler\n");
    }

    @Test
    public void nestedHandlersResolveInnermostFirst() {
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "try {\n"
                        + "  try {\n"
                        + "    throw AppError()\n"
                        + "  }\n"
                        + "  catch (e: AppError) {\n"
                        + "    println(\"inner\")\n"
                        + "  }\n"
                        + "}\n"
                        + "catch (e: RuntimeException) {\n"
                        + "  println(\"outer\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("inner\n");
    }

    @Test
    public void uncaughtThrowIsAGuestFailureNotInternalError() {
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "println(\"start\")\n"
                        + "throw AppError()\n"
                        + "println(\"unreachable\")\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.isInternalError()).as(result.failure.getMessage()).isFalse();
        assertThat(result.failure.isGuestException()).as(result.failure.getMessage()).isTrue();
        assertThat(result.failure.getMessage().contains("uncaught guest exception of class 'AppError'"))
                .as(result.failure.getMessage()).isTrue();
        assertThat(result.output).isEqualTo("start\n");
    }

    @Test
    public void finallyRunsOnBreakExit() {
        // The finally clause runs when the try body exits via break, and the break still takes effect.
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "var mutable i: Integer = 0\n"
                        + "while (true) {\n"
                        + "  i = i + 1\n"
                        + "  try {\n"
                        + "    println(\"in-try\")\n"
                        + "    break\n"
                        + "  }\n"
                        + "  finally {\n"
                        + "    println(\"finally-ran\")\n"
                        + "  }\n"
                        + "  println(\"unreachable-after-break\")\n"
                        + "}\n"
                        + "println(\"loop-exited\")\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("in-try\nfinally-ran\nloop-exited\n");
    }

    @Test
    public void finallyRunsOnContinueExit() {
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "var mutable i: Integer = 0\n"
                        + "while (i < 2) {\n"
                        + "  i = i + 1\n"
                        + "  try {\n"
                        + "    continue\n"
                        + "  }\n"
                        + "  finally {\n"
                        + "    println(\"cleanup-\" .. i)\n"
                        + "  }\n"
                        + "}\n"
                        + "println(\"done\")\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("cleanup-1\ncleanup-2\ndone\n");
    }

    @Test
    public void finallyRunsOnReturnExit() {
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "func g() {\n"
                        + "  try {\n"
                        + "    return\n"
                        + "  }\n"
                        + "  finally {\n"
                        + "    println(\"finally-on-return\")\n"
                        + "  }\n"
                        + "  println(\"unreachable\")\n"
                        + "}\n"
                        + "g()\n"
                        + "println(\"after\")\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("finally-on-return\nafter\n");
    }

    @Test
    public void finallyRunsOnPropagationExit() {
        // A '?' propagation that leaves the try block must still run the finally clause, and the
        // enclosing Result-returning function still receives the propagated Err.
        Result result = evaluate(
                "enum Result<T, E> {\n"
                        + "    Ok(T)\n"
                        + "    Err(E)\n"
                        + "}\n"
                        + "func maybeFail(): Result<Integer, String> {\n"
                        + "    return Result.Err(\"nope\")\n"
                        + "}\n"
                        + "func caller(): Result<Integer, String> {\n"
                        + "  try {\n"
                        + "    var v = maybeFail()?\n"
                        + "    println(\"got-\" .. v)\n"
                        + "  }\n"
                        + "  finally {\n"
                        + "    println(\"finally-on-prop\")\n"
                        + "  }\n"
                        + "  return Result.Ok(0)\n"
                        + "}\n"
                        + "var r = caller()\n"
                        + "println(\"isErr-\" .. r.isErr())\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("finally-on-prop\nisErr-true\n");
    }

    @Test
    public void finallyThrowSupersedesPendingControlExit() {
        // A pending break is discarded when the finally block throws; the throw escapes to the handler.
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "var mutable i: Integer = 0\n"
                        + "try {\n"
                        + "  while (true) {\n"
                        + "    i = i + 1\n"
                        + "    try {\n"
                        + "      break\n"
                        + "    }\n"
                        + "    finally {\n"
                        + "      throw AppError()\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n"
                        + "catch (e: AppError) {\n"
                        + "  println(\"caught-break-superseded\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("caught-break-superseded\n");
    }

    @Test
    public void finallyThrowReplacesInFlightThrow() {
        // Java last-abrupt-completion-wins: a throw from the finally clause replaces the in-flight
        // throw entirely (no suppression chain in an unchecked-exception language). The outer
        // handler sees the finally clause's exception, not the original.
        Result result = evaluate(
                "class AError extends RuntimeException {\n"
                        + "}\n"
                        + "class BError extends RuntimeException {\n"
                        + "}\n"
                        + "try {\n"
                        + "  try {\n"
                        + "    throw AError()\n"
                        + "  }\n"
                        + "  finally {\n"
                        + "    throw BError()\n"
                        + "  }\n"
                        + "}\n"
                        + "catch (a: AError) {\n"
                        + "  println(\"caught-A\")\n"
                        + "}\n"
                        + "catch (b: BError) {\n"
                        + "  println(\"caught-B\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("caught-B\n");
    }

    @Test
    public void finallyReturnReplacesTryReturn() {
        // Java: a finally clause that returns replaces the value being returned by the try block.
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "func f(): Integer {\n"
                        + "  try {\n"
                        + "    return 1\n"
                        + "  }\n"
                        + "  finally {\n"
                        + "    return 2\n"
                        + "  }\n"
                        + "}\n"
                        + "println(f())\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("2\n");
    }

    @Test
    public void finallyReturnDiscardsHandlerThrow() {
        // Java: the finally clause's return discards a throw raised in the handler.
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "class OtherError extends RuntimeException {\n"
                        + "}\n"
                        + "func f(): Integer {\n"
                        + "  try {\n"
                        + "    throw AppError()\n"
                        + "  }\n"
                        + "  catch (e: AppError) {\n"
                        + "    throw OtherError()\n"
                        + "  }\n"
                        + "  finally {\n"
                        + "    return 9\n"
                        + "  }\n"
                        + "}\n"
                        + "println(f())\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("9\n");
    }

    @Test
    public void tryBlockReturnSatisfiesTheReturnPathRule() {
        // A return inside a try block guarantees the function returns, matching javac's reachability
        // analysis for try statements (the finally clause cannot fall through in this shape).
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "func f(): Integer {\n"
                        + "  try {\n"
                        + "    return 1\n"
                        + "  }\n"
                        + "  finally {\n"
                        + "    println(\"fin\")\n"
                        + "  }\n"
                        + "}\n"
                        + "println(f())\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("fin\n1\n");
    }

    @Test
    public void finallyReturnAloneSatisfiesTheReturnPathRule() {
        // When the finally clause always transfers control, the try block itself needs no return.
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "func f(): Integer {\n"
                        + "  try {\n"
                        + "    println(\"body\")\n"
                        + "  }\n"
                        + "  finally {\n"
                        + "    return 7\n"
                        + "  }\n"
                        + "}\n"
                        + "println(f())\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("body\n7\n");
    }

    @Test
    public void handlerThatFallsThroughStillNeedsAReturnPath() {
        // javac rejects this shape too: a handler that completes normally leaves the statement
        // reachable, so the function still requires a return on that path.
        Result result = evaluate(
                "func f(): Integer {\n"
                        + "  try {\n"
                        + "    return 1\n"
                        + "  }\n"
                        + "  catch (e: RuntimeException) {\n"
                        + "    println(\"h\")\n"
                        + "  }\n"
                        + "}\n"
                        + "println(f())\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.getMessage().contains("SOLV-TYPE-012")).as(result.failure.getMessage()).isTrue();
        assertThat(result.output).as("a rejected program must not execute").isEqualTo("");
    }

    @Test
    public void finallyControlExitDiscardsInFlightThrow() {
        // Java: a finally clause that completes by break discards the in-flight throw; the original
        // exception never reaches the outer handler.
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "func loop() {\n"
                        + "  while (true) {\n"
                        + "    try {\n"
                        + "      throw AppError()\n"
                        + "    }\n"
                        + "    finally {\n"
                        + "      println(\"inner-finally\")\n"
                        + "      break\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n"
                        + "try {\n"
                        + "  loop()\n"
                        + "}\n"
                        + "catch (e: AppError) {\n"
                        + "  println(\"outer-caught\")\n"
                        + "}\n"
                        + "println(\"after\")\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("inner-finally\nafter\n");
    }

    @Test
    public void aHandlerBindingIsInitializedSoItCanBeRethrown() {
        // The handler only runs with a caught value bound to the name, so reading it is as valid as
        // reading a parameter; a plain rethrow must not be diagnosed as a read-before-initialize.
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "func raise(): Integer {\n"
                        + "  try {\n"
                        + "    throw AppError()\n"
                        + "  }\n"
                        + "  catch (e: AppError) {\n"
                        + "    throw e\n"
                        + "  }\n"
                        + "}\n"
                        + "try {\n"
                        + "  raise()\n"
                        + "}\n"
                        + "catch (outer: AppError) {\n"
                        + "  println(\"rethrow-reached-outer\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("rethrow-reached-outer\n");
    }

    @Test
    public void anOuterHandlerBindingSurvivesAnInnerHandler() {
        // The outer and inner bindings live in distinct frame slots, so the inner handler must not
        // overwrite the outer value: rethrowing the outer binding still delivers the original
        // Sub instance, not the Fatal one caught in between.
        Result result = evaluate(
                "mutable class Boom extends RuntimeException {\n"
                        + "}\n"
                        + "class Sub extends Boom {\n"
                        + "}\n"
                        + "class Fatal extends RuntimeException {\n"
                        + "}\n"
                        + "func raise(): Integer {\n"
                        + "  try {\n"
                        + "    throw Sub()\n"
                        + "  }\n"
                        + "  catch (e: Boom) {\n"
                        + "    try {\n"
                        + "      throw Fatal()\n"
                        + "    }\n"
                        + "    catch (inner: Fatal) {\n"
                        + "      throw e\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n"
                        + "try {\n"
                        + "  raise()\n"
                        + "}\n"
                        + "catch (outer: Fatal) {\n"
                        + "  println(\"slot-clobbered\")\n"
                        + "}\n"
                        + "catch (outer: Sub) {\n"
                        + "  println(\"original-value-intact\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("original-value-intact\n");
    }

    @Test
    public void aHandlerBindingCanBeStoredAndRethrown() {
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "func raise(): Integer {\n"
                        + "  try {\n"
                        + "    throw AppError()\n"
                        + "  }\n"
                        + "  catch (e: AppError) {\n"
                        + "    var saved = e\n"
                        + "    throw saved\n"
                        + "  }\n"
                        + "}\n"
                        + "try {\n"
                        + "  raise()\n"
                        + "}\n"
                        + "catch (outer: AppError) {\n"
                        + "  println(\"stored-and-rethrown\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("stored-and-rethrown\n");
    }

    @Test
    public void anExceptionHandlerCanReadTheMessageOfAThrownValue() {
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "try {\n"
                        + "  throw AppError(\"disk full\")\n"
                        + "}\n"
                        + "catch (e: AppError) {\n"
                        + "  println(\"msg=\" .. e.getMessage())\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("msg=disk full\n");
    }

    @Test
    public void anExceptionCarriesNoMessageUnlessOneIsSupplied() {
        // Both construction forms exist, matching Java's empty constructor and message constructor.
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "func show(e: AppError) {\n"
                        + "  try {\n"
                        + "    throw e\n"
                        + "  }\n"
                        + "  catch (caught: AppError) {\n"
                        + "    println(\"msg=\" .. caught.getMessage())\n"
                        + "  }\n"
                        + "}\n"
                        + "show(AppError())\n"
                        + "show(AppError(\"with message\"))\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("msg=null\nmsg=with message\n");
    }

    @Test
    public void theMessageCoexistsWithDeclaredConstructorArguments() {
        // The message is a trailing optional argument rather than a declared constructor parameter, so a
        // subclass keeps its own parameters and its super(...) forwarding unchanged.
        Result result = evaluate(
                "mutable class BaseError extends RuntimeException {\n"
                        + "  var code: Integer\n"
                        + "  BaseError(code: Integer) {\n"
                        + "    this.code = code\n"
                        + "  }\n"
                        + "}\n"
                        + "class SubError extends BaseError {\n"
                        + "  SubError(code: Integer) {\n"
                        + "    super(code)\n"
                        + "  }\n"
                        + "}\n"
                        + "try {\n"
                        + "  throw SubError(7, \"sub message\")\n"
                        + "}\n"
                        + "catch (e: SubError) {\n"
                        + "  println(\"code=\" .. e.code)\n"
                        + "  println(\"msg=\" .. e.getMessage())\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("code=7\nmsg=sub message\n");
    }

    @Test
    public void theMessageIsReadableThroughABaseTypeHandler() {
        // getMessage() belongs to every guest exception type, so a handler written on a base type reaches it.
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "func describe(e: Exception): String? {\n"
                        + "  return e.getMessage()\n"
                        + "}\n"
                        + "try {\n"
                        + "  throw AppError(\"via base\")\n"
                        + "}\n"
                        + "catch (e: RuntimeException) {\n"
                        + "  println(describe(e))\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("via base\n");
    }

    @Test
    public void aNullMessageArgumentIsAcceptedAndReadsAsNull() {
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "var none: String? = null\n"
                        + "try {\n"
                        + "  throw AppError(none)\n"
                        + "}\n"
                        + "catch (e: AppError) {\n"
                        + "  println(\"msg=\" .. e.getMessage())\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("msg=null\n");
    }

    @Test
    public void aSafeMessageReadOnANullReceiverIsSafe() {
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "var none: AppError? = null\n"
                        + "println(\"onNull=\" .. none?.getMessage())\n"
                        + "try {\n"
                        + "  throw AppError(\"safe\")\n"
                        + "}\n"
                        + "catch (e: AppError) {\n"
                        + "  println(\"onValue=\" .. e?.getMessage())\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("onNull=null\nonValue=safe\n");
    }

    @Test
    public void anUncaughtFailureReportsTheMessage() {
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "println(\"start\")\n"
                        + "throw AppError(\"disk full\")\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.isInternalError()).as(result.failure.getMessage()).isFalse();
        assertThat(result.failure.getMessage().contains("of class 'AppError' with message 'disk full'"))
                .as(result.failure.getMessage()).isTrue();
        assertThat(result.output).isEqualTo("start\n");
    }

    @Test
    public void theMessageArgumentMustBeAString() {
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "func raise() {\n"
                        + "  throw AppError(42)\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.getMessage().contains("SOLV-TYPE-001")).as(result.failure.getMessage()).isTrue();
        assertThat(result.output).as("a rejected program must not execute").isEqualTo("");
    }

    @Test
    public void anExceptionAcceptsAtMostOneMessageArgument() {
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "func raise() {\n"
                        + "  throw AppError(\"a\", \"b\")\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.getMessage().contains("SOLV-TYPE-003")).as(result.failure.getMessage()).isTrue();
        assertThat(result.output).as("a rejected program must not execute").isEqualTo("");
    }

    @Test
    public void theMessageFieldIsNotAReadableMember() {
        // The storage is private by construction: only the accessor reaches it, so `e.message` is not a
        // member of an exception class at all.
        Result result = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "}\n"
                        + "func show(e: AppError) {\n"
                        + "  println(e.message)\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.getMessage().contains("SOLV-RESOL-004")).as(result.failure.getMessage()).isTrue();
        assertThat(result.output).isEqualTo("");
    }

    @Test
    public void theSynthesizedMemberNamesAreReservedOnExceptionClasses() {
        Result field = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "  var message: String = \"mine\"\n"
                        + "}\n");
        assertThat(field.failure).as(field.output).isNotNull();
        assertThat(field.failure.getMessage().contains("SOLV-SEM-037")).as(field.failure.getMessage()).isTrue();

        Result accessor = evaluate(
                "class AppError extends RuntimeException {\n"
                        + "  func getMessage(): String {\n"
                        + "    return \"mine\"\n"
                        + "  }\n"
                        + "}\n");
        assertThat(accessor.failure).as(accessor.output).isNotNull();
        assertThat(accessor.failure.getMessage().contains("SOLV-SEM-037")).as(accessor.failure.getMessage()).isTrue();
    }

    @Test
    public void theSynthesizedMemberNamesAreOrdinaryMembersOnOtherClasses() {
        // The reservation applies only to guest exception types, so an ordinary class declares both names.
        Result result = evaluate(
                "class Note {\n"
                        + "  var message: String = \"fine\"\n"
                        + "  func getMessage(): String {\n"
                        + "    return \"fine\"\n"
                        + "  }\n"
                        + "}\n"
                        + "var note = Note()\n"
                        + "println(note.message)\n"
                        + "println(note.getMessage())\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("fine\nfine\n");
    }

    @Test
    public void getMessageIsNotAvailableOnNonExceptionClasses() {
        Result result = evaluate(
                "class Plain {\n"
                        + "}\n"
                        + "func show(p: Plain) {\n"
                        + "  println(p.getMessage())\n"
                        + "}\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.getMessage().contains("SOLV-RESOL-004")).as(result.failure.getMessage()).isTrue();
        assertThat(result.output).isEqualTo("");
    }
}
