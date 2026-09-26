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
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

/**
 * End-to-end execution tests for the postfix propagation operator {@code expression?} (error-handling
 * phases). Each case runs through the Truffle AST backend and observes behaviour through a top-level
 * {@code match}, so propagation is verified as returning the carried {@code Err} value from the
 * enclosing {@code Result}-returning function rather than as an unchecked runtime failure. Top-level
 * executable statements form the entry point, so results are observed there instead of via an explicit
 * {@code main}.
 */
public final class SolvikPropagationExecutionTest {

    private static String run(String source) {
        // Engine diagnostic streams are redirected to buffers; on a plain JIT-less JDK Truffle emits an
        // interpreter-only warning that would otherwise contaminate the asserted guest output.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik")
                .out(out)
                .err(new ByteArrayOutputStream())
                .option("engine.WarnInterpreterOnly", "false")
                .allowAllAccess(true)
                .build()) {
            context.eval(build(source, "propagation.sol"));
        } catch (org.graalvm.polyglot.PolyglotException e) {
            throw new AssertionError("eval failed: " + e.getMessage(), e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Source build(String source, String name) {
        try {
            return Source.newBuilder("solvik", source, name).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void propagationUnwrapsTheSuccessPayload() {
        assertThat(run("""
                enum Result<T, E> {
                    Ok(T)
                    Err(E)
                }
                func four(): Result<Integer, Integer> {
                    return Result.Ok(4)
                }
                func addOne(): Result<Integer, Integer> {
                    val source: Integer = four()?
                    return Result.Ok(source + 1)
                }

                    val message = match addOne() {
                        Ok(value) => "ok " .. value
                        Err(code) => "err " .. code
                    }
                    println(message)
                """)).isEqualTo("ok 5\n");
    }

    @Test
    public void propagationReturnsTheCarriedErrorFromTheEnclosingFunction() {
        assertThat(run("""
                enum Result<T, E> {
                    Ok(T)
                    Err(E)
                }
                func boom(): Result<Integer, String> {
                    return Result.Err("boom")
                }
                func propagate(): Result<Integer, String> {
                    val source: Integer = boom()?
                    return Result.Ok(source)
                }

                    val message = match propagate() {
                        Ok(value) => "ok " .. value
                        Err(messageText) => "err " .. messageText
                    }
                    println(message)
                """)).isEqualTo("err boom\n");
    }

    @Test
    public void propagationComposesAcrossNestedResultFunctions() {
        assertThat(run("""
                enum Result<T, E> {
                    Ok(T)
                    Err(E)
                }
                func deepest(): Result<Integer, Integer> {
                    return Result.Err(42)
                }
                func middle(): Result<Integer, Integer> {
                    val source: Integer = deepest()?
                    return Result.Ok(source + 1)
                }
                func top(): Result<Integer, Integer> {
                    val source: Integer = middle()?
                    return Result.Ok(source + 1)
                }

                    val message = match top() {
                        Ok(value) => "ok " .. value
                        Err(code) => "err " .. code
                    }
                    println(message)
                """)).isEqualTo("err 42\n");
    }

    @Test
    public void propagationPreservesTheUnderlyingTypedErrorValue() {
        // The propagated error keeps its concrete payload; it is not widened or replaced.
        assertThat(run("""
                enum Result<T, E> {
                    Ok(T)
                    Err(E)
                }
                func config(): Result<String, String> {
                    return Result.Err("missing file")
                }
                func propagate(): Result<String, String> {
                    val source: String = config()?
                    return Result.Ok(source)
                }

                    val message = match propagate() {
                        Ok(value) => "ok " .. value
                        Err(messageText) => "err " .. messageText
                    }
                    println(message)
                """)).isEqualTo("err missing file\n");
    }

    @Test
    public void propagationDoesNotEvaluateExpressionsAfterAnError() {
        // The tail of the function must never run after an Err propagates: reaching it would produce
        // Ok(101) instead of the propagated Err(1).
        assertThat(run("""
                enum Result<T, E> {
                    Ok(T)
                    Err(E)
                }
                func boom(): Result<Integer, Integer> {
                    return Result.Err(1)
                }
                func work(): Result<Integer, Integer> {
                    val source: Integer = boom()?
                    return Result.Ok(source + 100)
                }

                    val message = match work() {
                        Ok(value) => "ok " .. value
                        Err(code) => "err " .. code
                    }
                    println(message)
                """)).isEqualTo("err 1\n");
    }
}
