/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
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
 * End-to-end tests for the predeclared {@code exit(code: Int)} function: calling it terminates
 * the Solvik context with the given status, which the GraalVM polyglot engine surfaces as an exit
 * {@code PolyglotException}, and no later statement runs. A program that does not call {@code exit}
 * completes normally.
 */
public final class SolvikExitTest {

    private static Source build(String text) {
        try {
            return Source.newBuilder("solvik", text, "exit.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void exitTerminatesWithTheGivenStatus() {
        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            context.eval(build("    exit(5)\n"));
            fail("exit must terminate the program");
        } catch (PolyglotException ex) {
            assertTrue("exception must be an exit", ex.isExit());
            assertEquals(5, ex.getExitStatus());
        }
    }

    @Test
    public void exitStopsExecutionAndFlushesPriorOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build("    println(\"before\")\n    exit(3)\n    println(\"after\")\n"));
            fail("exit must terminate the program");
        } catch (PolyglotException ex) {
            assertTrue("exception must be an exit", ex.isExit());
            assertEquals(3, ex.getExitStatus());
        }
        assertEquals("before\n", out.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void normalCompletionExitsWithoutAnException() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).allowAllAccess(true).build()) {
            context.eval(build("    println(\"done\")\n"));
        }
        assertEquals("done\n", out.toString(StandardCharsets.UTF_8));
    }
}
