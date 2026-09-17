/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.launcher.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.graalvm.polyglot.Source;
import org.junit.Test;
import org.solvik.launcher.SolvikMain;

/**
 * JVM distribution/launcher validation (docs/IMPLEMENTATION_PLAN.md Phase 16): the Solvik launcher
 * evaluates a source file, reports a compile error with its stable diagnostic code, returns a
 * process exit code, and exposes only the {@code solvik} language.
 */
public final class SolvikMainTest {

    private static Source source(String text, String name) throws IOException {
        return Source.newBuilder("solvik", text, name).build();
    }

    private static int run(String text, PrintStream out, PrintStream err) throws IOException {
        return SolvikMain.executeSource(source(text, "launcher.sol"), new ByteArrayInputStream(new byte[0]), out, err, Map.of());
    }

    @Test
    public void runsASolvikProgramAndReturnsZero() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("func main(): Unit {\n    println(\"launcher\")\n}\n", new PrintStream(out), new PrintStream(err));
        assertEquals(0, code);
        assertEquals("launcher\n", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void compileErrorReturnsOneAndWritesTheStableDiagnostic() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("func main(): Unit {\n    val x: Int = \"no\"\n}\n", new PrintStream(out), new PrintStream(err));
        assertEquals(1, code);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        String message = err.toString(StandardCharsets.UTF_8);
        assertTrue(message, message.contains("SOLV-TYPE-001"));
    }

    @Test
    public void launcherPrintsNoInterpreterInformation() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("func main(): Unit {\n    println(\"only program output\")\n}\n", new PrintStream(out), new PrintStream(err));
        assertEquals(0, code);
        assertEquals("only program output\n", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void silentProgramProducesNoOutput() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("func main(): Unit {\n}\n", new PrintStream(out), new PrintStream(err));
        assertEquals(0, code);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }
}
