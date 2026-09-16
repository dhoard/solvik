/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
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
        Result result = evaluate("fun main(): Unit {\n  println(1)\n  val x: Int = \"no\"\n}\n");
        assertNotNull(result.failure);
        assertTrue(result.failure.isSyntaxError());
        assertTrue(result.failure.getMessage().contains("SOLV-TYPE-001"));
        assertEquals("ill-typed programs must not execute", "", result.output);
    }

    @Test
    public void unknownNameIsReportedBeforeExecution() {
        Result result = evaluate("fun main(): Unit {\n  println(missing)\n}\n");
        assertNotNull(result.failure);
        assertTrue(result.failure.getMessage().contains("SOLV-RESOL-001"));
        assertEquals("", result.output);
    }

    @Test
    public void nonBooleanConditionIsRejected() {
        Result result = evaluate("fun main(): Unit {\n  if (1) {\n    println(1)\n  }\n}\n");
        assertNotNull(result.failure);
        assertTrue(result.failure.getMessage(), result.failure.getMessage().contains("SOLV-TYPE-005"));
        assertEquals("", result.output);
    }

    @Test
    public void invalidEntryPointIsRejected() {
        Result result = evaluate("fun main(a: Int): Unit {\n  println(a)\n}\n");
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
        Result result = evaluate("fun main(): Unit {\n  println(1 / 0)\n}\n");
        assertNotNull(result.failure);
        assertFalse(result.failure.isSyntaxError());
        assertTrue(result.failure.getMessage(), result.failure.getMessage().contains("division by zero"));
    }

    @Test
    public void integerOverflowRaisesRuntimeArithmeticError() {
        Result result = evaluate("fun main(): Unit {\n  println(2147483647 + 1)\n}\n");
        assertNotNull(result.failure);
        assertFalse(result.failure.isSyntaxError());
        assertTrue(result.failure.getMessage(), result.failure.getMessage().contains("overflow"));
    }

    @Test
    public void invalidStringEscapeIsRejected() {
        Result result = evaluate("fun main(): Unit {\n  println(\"bad\\qescape\")\n}\n");
        assertNotNull(result.failure);
        assertTrue(result.failure.getMessage().contains("SOLV-LEX-003"));
        assertEquals("", result.output);
    }

    @Test
    public void assignmentAsExpressionIsRejected() {
        Result result = evaluate("fun main(): Unit {\n  var x = 1\n  val y = (x = 2)\n  println(y)\n}\n");
        assertNotNull(result.failure);
        assertTrue(result.failure.isSyntaxError());
        assertEquals("", result.output);
    }
}
