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
 * Compile-only boundary validation. The TCK requires a genuine compile-only mode that performs full
 * static validation without executing application code, and a machine-readable diagnostics channel.
 * These tests prove both: a valid program that would print and exit if run produces zero observables
 * and exit {@code 0}; an invalid program produces a stable {@code SOLV-*} code both as human stderr
 * and as structured JSON fields, with the sentinels that would print if executed still silent.
 */
public final class SolvikCompileOnlyTest {

    private static int runArgs(String[] args, String stdin, ByteArrayOutputStream out, ByteArrayOutputStream err) throws IOException {
        return SolvikMain.run(args, new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)), new PrintStream(out), new PrintStream(err), null);
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
    public void compileOnlyDoesNotExecutePrintlnOrExitOnAValidProgram() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        // The program would print SIDE_EFFECT and exit 3 if executed; compile-only must run neither.
        int code = runArgs(new String[]{"--compile-only"}, "println(\"SIDE_EFFECT\")\nexit(3)\n", out, err);
        assertThat(code).as("compile-only of a valid program succeeds").isEqualTo(0);
        assertThat(out.toString(StandardCharsets.UTF_8)).as("no application output").isEmpty();
        assertThat(err.toString(StandardCharsets.UTF_8)).as("no diagnostics").isEmpty();
    }

    @Test
    public void compileOnlyReportsAStableCodeAsStructuredJson() throws IOException {
        Path directory = Files.createTempDirectory("solvik-compile-only-json");
        try {
            Path source = directory.resolve("bad.sol");
            // The trailing println is a sentinel: it must never run, so stdout stays empty.
            Files.writeString(source, "val x: Integer = \"no\"\nprintln(\"NEVER\")\n", StandardCharsets.UTF_8);
            Path json = directory.resolve("diag.json");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int code = runArgs(new String[]{"--compile-only", "--diagnostics-json=" + json.toAbsolutePath(), source.toAbsolutePath().toString()}, "", out, err);
            assertThat(code).isEqualTo(1);
            assertThat(out.toString(StandardCharsets.UTF_8)).as("sentinel output never produced").isEmpty();
            String human = err.toString(StandardCharsets.UTF_8);
            assertThat(human.contains("SOLV-TYPE-001")).as(human).isTrue();

            String body = Files.readString(json, StandardCharsets.UTF_8).trim();
            assertThat(body).contains("\"phase\":\"compile\"").contains("\"status\":\"COMPILE_ERROR\"").contains("\"code\":\"SOLV-TYPE-001\"").contains("\"family\":\"TYPE\"");
            assertThat(body).contains("\"entryFile\":\"" + source.getFileName() + "\"");
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    public void compileOnlyWritesHumanStderrWithoutTheJsonChannel() throws IOException {
        Path directory = Files.createTempDirectory("solvik-compile-only-human");
        try {
            Path source = directory.resolve("bad.sol");
            Files.writeString(source, "val x: Integer = \"no\"\n", StandardCharsets.UTF_8);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int code = runArgs(new String[]{"--compile-only", source.toAbsolutePath().toString()}, "", out, err);
            assertThat(code).isEqualTo(1);
            assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
            assertThat(err.toString(StandardCharsets.UTF_8).contains("SOLV-TYPE-001")).as(err.toString(StandardCharsets.UTF_8)).isTrue();
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    public void compileOnlyReadsTheProgramFromStandardInput() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = runArgs(new String[]{"--compile-only"}, "val ok: Integer = 5\nprintln(\"no\")\n", out, err);
        assertThat(code).isEqualTo(0);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    public void compileOnlyRejectsAnUnknownLanguageOptionWithoutExecuting() throws IOException {
        // The unknown solvik.* option makes context construction fail, so compile-only reports it and
        // runs nothing. This exercises the option-rejection path of the compile-only entry point.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = runArgs(new String[]{"--solvik.noSuchOption", "--compile-only"}, "println(1)\n", out, err);
        assertThat(code).isEqualTo(1);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(err.toString(StandardCharsets.UTF_8).contains("noSuchOption")).as(err.toString(StandardCharsets.UTF_8)).isTrue();
    }

    @Test
    public void compileSourceEntryPointValidatesWithoutOptionsMapMutation() throws IOException {
        // Exercises compileSource directly with a caller-supplied options map, so compile-only works
        // when invoked programmatically rather than through argument parsing.
        Path directory = Files.createTempDirectory("solvik-compile-only-direct");
        try {
            Path source = directory.resolve("ok.sol");
            Files.writeString(source, "println(\"direct\")\nexit(9)\n", StandardCharsets.UTF_8);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int code = SolvikMain.compileSource(Source.newBuilder("solvik", source.toFile()).build(), new PrintStream(out), new PrintStream(err), new java.util.HashMap<>(), null);
            assertThat(code).isEqualTo(0);
            assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    public void normalExecutionStillRunsTheProgram() throws IOException {
        // Guards that adding the compile-only flags did not change default behavior.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = runArgs(new String[]{}, "println(\"ran\")\n", out, err);
        assertThat(code).isEqualTo(0);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("ran\n");
    }

    @Test
    public void missingIncludeIsReportedByCodeInCompileOnlyMode() throws IOException {
        Path directory = Files.createTempDirectory("solvik-compile-only-include");
        try {
            Path source = directory.resolve("main.sol");
            Files.writeString(source, "include \"nope.sol\"\n", StandardCharsets.UTF_8);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int code = runArgs(new String[]{"--compile-only", source.toAbsolutePath().toString()}, "", out, err);
            assertThat(code).isEqualTo(1);
            assertThat(err.toString(StandardCharsets.UTF_8).contains("SOLV-RESOL-008")).as(err.toString(StandardCharsets.UTF_8)).isTrue();
        } finally {
            deleteRecursively(directory);
        }
    }
}
