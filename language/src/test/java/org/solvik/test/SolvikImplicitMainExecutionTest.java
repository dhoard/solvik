/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * End-to-end tests for executable top-level statements: a file's bare statements form an implicit
 * {@code func main(): Unit} (docs/LANGUAGE_SPEC.md section 6) and run end to end, in source order,
 * alongside declarations from the same file.
 */
public final class SolvikImplicitMainExecutionTest {

    private static String run(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(text));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Source build(String text) {
        try {
            return Source.newBuilder("solvik", text, "implicit.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void bareStatementsExecute() {
        assertEquals("hi\n", run("println(\"hi\")\n"));
    }

    @Test
    public void topLevelLocalsAndLoopsRun() {
        assertEquals("6\n", run("""
                var total = 0
                for (var i = 1; i <= 3; i = i + 1) {
                    total = total + i
                }
                println(total)
                """));
    }

    @Test
    public void implicitMainCallsFunctionsDeclaredLaterInTheFile() {
        assertEquals("3\n", run("""
                println(add(1, 2))
                func add(a: Int, b: Int): Int {
                    return a + b
                }
                """));
    }

    @Test
    public void declarationsAndStatementsRunInSourceOrder() {
        assertEquals("a\nb\n", run("""
                println("a")
                func f(): Unit {
                    println("b")
                }
                f()
                """));
    }

    @Test
    public void bareExitTerminatesWithTheGivenStatus() {
        try (Context context = Context.newBuilder("solvik").allowAllAccess(true).build()) {
            context.eval(build("println(\"before\")\nexit(4)\nprintln(\"after\")\n"));
            fail("exit must terminate the program");
        } catch (PolyglotException ex) {
            assertTrue("exception must be an exit", ex.isExit());
            assertEquals(4, ex.getExitStatus());
        }
    }
}
