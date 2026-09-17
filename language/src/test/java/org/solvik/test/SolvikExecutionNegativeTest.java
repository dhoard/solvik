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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Test;

/**
 * Negative Phase 5 tests: compile-time errors are reported before lowering and produce no output,
 * runtime arithmetic errors surface as Solvik exceptions, and representative SimpleLanguage-only
 * syntax is rejected with no compatibility path.
 */
public final class SolvikExecutionNegativeTest {

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
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source));
        } catch (PolyglotException e) {
            failure = e;
        }
        return new Result(out.toString(StandardCharsets.UTF_8), failure);
    }

    private static Source build(String source) {
        try {
            return Source.newBuilder("solvik", source, "negative.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void typeErrorIsReportedBeforeAnyOutput() {
        Result result = evaluate("  println(1)\n  val x: Int = \"no\"\n");
        assertNotNull(result.failure);
        assertTrue(result.failure.isSyntaxError());
        assertTrue(result.failure.getMessage().contains("SOLV-TYPE-001"));
        assertEquals("ill-typed programs must not execute", "", result.output);
    }

    @Test
    public void unknownNameIsReportedBeforeExecution() {
        Result result = evaluate("  println(missing)\n");
        assertNotNull(result.failure);
        assertTrue(result.failure.getMessage().contains("SOLV-RESOL-001"));
        assertEquals("", result.output);
    }

    @Test
    public void nonBooleanConditionIsRejected() {
        Result result = evaluate("  if (1) {\n    println(1)\n  }\n");
        assertNotNull(result.failure);
        assertTrue(result.failure.getMessage(), result.failure.getMessage().contains("SOLV-TYPE-005"));
        assertEquals("", result.output);
    }

    @Test
    public void invalidEntryPointIsRejected() {
        Result result = evaluate("func main(a: Int): Unit {\n  println(a)\n}\n");
        assertNotNull(result.failure);
        assertTrue(result.failure.getMessage().contains("SOLV-SEM-001"));
        assertEquals("", result.output);
    }

    @Test
    public void simpleLanguageFunctionSyntaxIsRejected() {
        Result result = evaluate("function main() { return 1; }\n");
        assertNotNull(result.failure);
        assertTrue(result.failure.isSyntaxError());
        assertTrue(result.failure.getMessage().contains("SOLV-PARS-004"));
        assertEquals("", result.output);
    }

    @Test
    public void divisionByZeroRaisesRuntimeArithmeticError() {
        Result result = evaluate("  println(1 / 0)\n");
        assertNotNull(result.failure);
        assertFalse(result.failure.isSyntaxError());
        assertTrue(result.failure.getMessage(), result.failure.getMessage().contains("division by zero"));
    }

    @Test
    public void integerOverflowRaisesRuntimeArithmeticError() {
        Result result = evaluate("  println(2147483647 + 1)\n");
        assertNotNull(result.failure);
        assertFalse(result.failure.isSyntaxError());
        assertTrue(result.failure.getMessage(), result.failure.getMessage().contains("overflow"));
    }

    @Test
    public void invalidStringEscapeIsRejected() {
        Result result = evaluate("  println(\"bad\\qescape\")\n");
        assertNotNull(result.failure);
        assertTrue(result.failure.getMessage().contains("SOLV-LEX-003"));
        assertEquals("", result.output);
    }

    @Test
    public void assignmentAsExpressionIsRejected() {
        Result result = evaluate("  var x = 1\n  val y = (x = 2)\n  println(y)\n");
        assertNotNull(result.failure);
        assertTrue(result.failure.isSyntaxError());
        assertEquals("", result.output);
    }
}
