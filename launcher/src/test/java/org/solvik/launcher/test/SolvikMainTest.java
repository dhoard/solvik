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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
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

    private static int runFile(Path file, PrintStream out, PrintStream err) throws IOException {
        return SolvikMain.executeSource(Source.newBuilder("solvik", file.toFile()).build(), new ByteArrayInputStream(new byte[0]), out, err, Map.of());
    }

    private static void deleteRecursively(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }

    @Test
    public void fileBackedProgramRunsItsSiblingInclude() throws IOException {
        Path directory = Files.createTempDirectory("solvik-launcher-include");
        try {
            Files.writeString(directory.resolve("lib.sol"), "func helper(): Int {\n    return 11\n}\n", StandardCharsets.UTF_8);
            Path root = directory.resolve("main.sol");
            Files.writeString(root, "include \"lib.sol\"\nprintln(helper())\n", StandardCharsets.UTF_8);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int code = runFile(root, new PrintStream(out), new PrintStream(err));
            assertThat(code).isEqualTo(0);
            assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("11\n");
            assertThat(err.toString(StandardCharsets.UTF_8)).isEqualTo("");
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    public void missingIncludeReturnsStatusOneAndReportsDiagnostic() throws IOException {
        Path directory = Files.createTempDirectory("solvik-launcher-missing");
        try {
            Path root = directory.resolve("main.sol");
            Files.writeString(root, "include \"missing.sol\"\n", StandardCharsets.UTF_8);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int code = runFile(root, new PrintStream(out), new PrintStream(err));
            assertThat(code).isEqualTo(1);
            assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("");
            assertThat(err.toString(StandardCharsets.UTF_8).contains("SOLV-RESOL-008")).as(err.toString(StandardCharsets.UTF_8)).isTrue();
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    public void runsASolvikProgramAndReturnsZero() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("    println(\"launcher\")\n", new PrintStream(out), new PrintStream(err));
        assertThat(code).isEqualTo(0);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("launcher\n");
        assertThat(err.toString(StandardCharsets.UTF_8)).isEqualTo("");
    }

    @Test
    public void compileErrorReturnsOneAndWritesTheStableDiagnostic() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("    val x: Int = \"no\"\n", new PrintStream(out), new PrintStream(err));
        assertThat(code).isEqualTo(1);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("");
        String message = err.toString(StandardCharsets.UTF_8);
        assertThat(message.contains("SOLV-TYPE-001")).as(message).isTrue();
    }

    @Test
    public void launcherPrintsNoInterpreterInformation() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("    println(\"only program output\")\n", new PrintStream(out), new PrintStream(err));
        assertThat(code).isEqualTo(0);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("only program output\n");
        assertThat(err.toString(StandardCharsets.UTF_8)).isEqualTo("");
    }

    @Test
    public void silentProgramProducesNoOutput() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("", new PrintStream(out), new PrintStream(err));
        assertThat(code).isEqualTo(0);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("");
        assertThat(err.toString(StandardCharsets.UTF_8)).isEqualTo("");
    }

    @Test
    public void exitSetsTheProcessExitCode() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("    println(\"before\")\n    exit(7)\n    println(\"after\")\n", new PrintStream(out), new PrintStream(err));
        assertThat(code).isEqualTo(7);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("before\n");
        assertThat(err.toString(StandardCharsets.UTF_8)).isEqualTo("");
    }

    @Test
    public void explicitExitZeroReturnsZero() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("    exit(0)\n", new PrintStream(out), new PrintStream(err));
        assertThat(code).isEqualTo(0);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("");
        assertThat(err.toString(StandardCharsets.UTF_8)).isEqualTo("");
    }

    @Test
    public void bareTopLevelStatementsRunWithoutAnExplicitMain() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("println(\"bare\")\n", new PrintStream(out), new PrintStream(err));
        assertThat(code).isEqualTo(0);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("bare\n");
        assertThat(err.toString(StandardCharsets.UTF_8)).isEqualTo("");
    }

    @Test
    public void bareExitSetsTheProcessExitCode() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = run("println(\"before\")\nexit(6)\n", new PrintStream(out), new PrintStream(err));
        assertThat(code).isEqualTo(6);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("before\n");
        assertThat(err.toString(StandardCharsets.UTF_8)).isEqualTo("");
    }
}
