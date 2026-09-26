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
import org.graalvm.polyglot.SourceSection;
import org.junit.jupiter.api.Test;

/**
 * End-to-end execution tests for the {@code Result} error-handling operations {@code isOk},
 * {@code isErr}, {@code unwrap}, {@code unwrapErr}, {@code expect}, and {@code ignore}
 * (docs/LANGUAGE_SPEC.md error-handling operations). Positive cases observe behaviour through the
 * shipped guest stream; negative cases assert the rejected program is diagnosed before execution with
 * a stable code and a real source span, and the wrong-variant unwrap/expect faults are reported as
 * ordinary guest failures (never internal errors).
 */
public final class SolvikResultOperationsExecutionTest {

    /** A two-variant {@code Result} declaration reused across cases (variants on separate lines for semicolon insertion). */
    private static final String RESULT_DECL = "enum Result<T, E> {\n    Ok(T)\n    Err(E)\n}\n";

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
            return Source.newBuilder("solvik", source, "result-operations.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void isOkAndIsErrReportVariant() {
        Result result = evaluate(RESULT_DECL
                + "func good(): Result<Integer, String> {\n    return Result.Ok(1)\n}\n"
                + "func bad(): Result<Integer, String> {\n    return Result.Err(\"x\")\n}\n"
                + "println(good().isOk())\n"
                + "println(good().isErr())\n"
                + "println(bad().isOk())\n"
                + "println(bad().isErr())\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("true\nfalse\nfalse\ntrue\n");
    }

    @Test
    public void unwrapReturnsSuccessPayload() {
        Result result = evaluate(RESULT_DECL
                + "func good(): Result<Integer, String> {\n    return Result.Ok(42)\n}\n"
                + "println(good().unwrap())\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("42\n");
    }

    @Test
    public void unwrapErrReturnsErrorPayload() {
        Result result = evaluate(RESULT_DECL
                + "func bad(): Result<Integer, String> {\n    return Result.Err(\"boom\")\n}\n"
                + "println(bad().unwrapErr())\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("boom\n");
    }

    @Test
    public void expectReturnsSuccessPayloadWhenOk() {
        Result result = evaluate(RESULT_DECL
                + "func good(): Result<Integer, String> {\n    return Result.Ok(7)\n}\n"
                + "println(good().expect(\"unused\"))\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("7\n");
    }

    @Test
    public void ignoreConsumesResultOnBothVariants() {
        // ignore() must satisfy the must-consume rule (SEM-052) on Ok and Err and yield Unit,
        // so the program runs to completion with no unused-Result diagnostic.
        Result result = evaluate(RESULT_DECL
                + "func good(): Result<Integer, String> {\n    return Result.Ok(1)\n}\n"
                + "func bad(): Result<Integer, String> {\n    return Result.Err(\"x\")\n}\n"
                + "good().ignore()\n"
                + "bad().ignore()\n"
                + "println(\"done\")\n");
        assertThat(result.failure).as(result.output).isNull();
        assertThat(result.output).isEqualTo("done\n");
    }

    @Test
    public void unwrapOnErrIsAGuestFailureNotInternalError() {
        Result result = evaluate(RESULT_DECL
                + "func bad(): Result<Integer, String> {\n    return Result.Err(\"boom\")\n}\n"
                + "println(bad().unwrap())\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.isInternalError()).as(result.failure.getMessage()).isFalse();
        assertThat(result.failure.isGuestException()).as(result.failure.getMessage()).isTrue();
        assertThat(result.failure.getMessage().contains("unwrap")).as(result.failure.getMessage()).isTrue();
        assertThat(result.output).as("the unwrap fault precedes any output").isEqualTo("");
    }

    @Test
    public void unwrapErrOnOkIsAGuestFailure() {
        Result result = evaluate(RESULT_DECL
                + "func good(): Result<Integer, String> {\n    return Result.Ok(7)\n}\n"
                + "println(good().unwrapErr())\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.isInternalError()).as(result.failure.getMessage()).isFalse();
        assertThat(result.failure.getMessage().contains("unwrapErr")).as(result.failure.getMessage()).isTrue();
    }

    @Test
    public void expectOnErrReportsMessageAndError() {
        Result result = evaluate(RESULT_DECL
                + "func bad(): Result<Integer, String> {\n    return Result.Err(\"boom\")\n}\n"
                + "println(bad().expect(\"could not load\"))\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.isInternalError()).as(result.failure.getMessage()).isFalse();
        assertThat(result.failure.getMessage().contains("could not load")).as(result.failure.getMessage()).isTrue();
        assertThat(result.failure.getMessage().contains("boom")).as(result.failure.getMessage()).isTrue();
    }

    @Test
    public void unknownMemberOnResultIsRejectedWithLocation() {
        Result result = evaluate(RESULT_DECL
                + "func good(): Result<Integer, String> {\n    return Result.Ok(1)\n}\n"
                + "println(good().nope())\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.getMessage().contains("SOLV-RESOL-004")).as(result.failure.getMessage()).isTrue();
        assertSpanAvailable(result.failure);
        assertThat(result.output).as("an ill-formed program must not execute").isEqualTo("");
    }

    @Test
    public void operationArityMismatchIsRejectedWithLocation() {
        // isOk takes no argument; supplying one is an arity error on the call.
        Result result = evaluate(RESULT_DECL
                + "func good(): Result<Integer, String> {\n    return Result.Ok(1)\n}\n"
                + "println(good().isOk(5))\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.getMessage().contains("SOLV-TYPE-003")).as(result.failure.getMessage()).isTrue();
        assertSpanAvailable(result.failure);
        assertThat(result.output).isEqualTo("");
    }

    @Test
    public void expectWithWrongMessageTypeIsRejected() {
        Result result = evaluate(RESULT_DECL
                + "func good(): Result<Integer, String> {\n    return Result.Ok(1)\n}\n"
                + "println(good().expect(123))\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.getMessage().contains("SOLV-TYPE")).as(result.failure.getMessage()).isTrue();
        assertSpanAvailable(result.failure);
        assertThat(result.output).isEqualTo("");
    }

    @Test
    public void resultOperationReadWithoutCallIsMethodAsValue() {
        // A member read of a Result operation (no call) is a method used as a value.
        Result result = evaluate(RESULT_DECL
                + "func good(): Result<Integer, String> {\n    return Result.Ok(1)\n}\n"
                + "val op = good().unwrap\n");
        assertThat(result.failure).as(result.output).isNotNull();
        assertThat(result.failure.getMessage().contains("SOLV-TYPE-014")).as(result.failure.getMessage()).isTrue();
        assertSpanAvailable(result.failure);
        assertThat(result.output).isEqualTo("");
    }

    private static void assertSpanAvailable(PolyglotException failure) {
        SourceSection section = failure.getSourceLocation();
        assertThat(section.hasCharIndex()).as("diagnostic must carry a source span").isTrue();
        assertThat(section.getCharIndex() >= 0).as("diagnostic span start must be valid").isTrue();
    }
}
