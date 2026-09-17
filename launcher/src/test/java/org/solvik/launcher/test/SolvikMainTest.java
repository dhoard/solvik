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
 * JVM distribution/launcher validation: the Solvik launcher
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
        int code = run("    println(\"launcher\")\n", new PrintStream(out), new PrintStream(err));
        assertEquals(0, code);
        assertEquals("launcher\n", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void compileErrorReturnsOneAndWritesTheStableDiagnostic() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("    val x: Int = \"no\"\n", new PrintStream(out), new PrintStream(err));
        assertEquals(1, code);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        String message = err.toString(StandardCharsets.UTF_8);
        assertTrue(message, message.contains("SOLV-TYPE-001"));
    }

    @Test
    public void launcherPrintsNoInterpreterInformation() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("    println(\"only program output\")\n", new PrintStream(out), new PrintStream(err));
        assertEquals(0, code);
        assertEquals("only program output\n", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void silentProgramProducesNoOutput() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("", new PrintStream(out), new PrintStream(err));
        assertEquals(0, code);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void exitSetsTheProcessExitCode() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("    println(\"before\")\n    exit(7)\n    println(\"after\")\n", new PrintStream(out), new PrintStream(err));
        assertEquals(7, code);
        assertEquals("before\n", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void explicitExitZeroReturnsZero() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("    exit(0)\n", new PrintStream(out), new PrintStream(err));
        assertEquals(0, code);
        assertEquals("", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void bareTopLevelStatementsRunWithoutAnExplicitMain() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("println(\"bare\")\n", new PrintStream(out), new PrintStream(err));
        assertEquals(0, code);
        assertEquals("bare\n", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }

    @Test
    public void bareExitSetsTheProcessExitCode() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("println(\"before\")\nexit(6)\n", new PrintStream(out), new PrintStream(err));
        assertEquals(6, code);
        assertEquals("before\n", out.toString(StandardCharsets.UTF_8));
        assertEquals("", err.toString(StandardCharsets.UTF_8));
    }
}
