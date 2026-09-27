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
import java.util.stream.Stream;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;
import org.solvik.launcher.RunResultJson;
import org.solvik.launcher.SolvikMain;

/**
 * Structured execute outcome validation. The TCK requires the launcher to expose a machine-readable
 * execute result that lets a consuming adapter distinguish {@code exit(n)} (a language exit) from a
 * runtime failure from a caught internal failure, without parsing human-readable stderr text or
 * relying only on the process exit code (which cannot separate {@code exit(1)} from a crash). These
 * tests exercise every branch of the structured channel and the pure-serializer helpers.
 */
public final class SolvikRunJsonTest {

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
    public void normalCompletionReportsLanguageExitZero() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        Path directory = Files.createTempDirectory("solvik-run-json-normal");
        try {
            Path source = directory.resolve("ok.sol");
            Files.writeString(source, "print(\"ok\")\n", StandardCharsets.UTF_8);
            Path json = directory.resolve("r.json");
            int code = runArgs(new String[]{"--run-json=" + json.toAbsolutePath(), source.toAbsolutePath().toString()}, "", out, err);
            assertThat(code).isEqualTo(0);
            String body = Files.readString(json, StandardCharsets.UTF_8).trim();
            assertThat(body).contains("\"phase\":\"execute\"").contains("\"status\":\"NORMAL_EXIT\"").contains("\"languageExit\":0");
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    public void exitNReportsLanguageExitNotRuntimeFailure() throws IOException {
        // exit(3) is a normal completion at status 3; it must be reported as NORMAL_EXIT, not
        // RUNTIME_FAILURE, and must not be confused with an implicit status-1 error path.
        Path directory = Files.createTempDirectory("solvik-run-json-exit");
        try {
            Path source = directory.resolve("exit.sol");
            Files.writeString(source, "exit(3)\n", StandardCharsets.UTF_8);
            Path json = directory.resolve("r.json");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int code = runArgs(new String[]{"--run-json=" + json.toAbsolutePath(), source.toAbsolutePath().toString()}, "", out, err);
            assertThat(code).isEqualTo(3);
            String body = Files.readString(json, StandardCharsets.UTF_8).trim();
            assertThat(body).contains("\"status\":\"NORMAL_EXIT\"").contains("\"languageExit\":3").doesNotContain("RUNTIME_FAILURE");
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    public void runtimeFailureCarriesAStructuredCategory() throws IOException {
        Path directory = Files.createTempDirectory("solvik-run-json-runtime");
        try {
            Path source = directory.resolve("arith.sol");
            Files.writeString(source, "println(1/0)\n", StandardCharsets.UTF_8);
            Path json = directory.resolve("r.json");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ByteArrayOutputStream err = new ByteArrayOutputStream();
            int code = runArgs(new String[]{"--run-json=" + json.toAbsolutePath(), source.toAbsolutePath().toString()}, "", out, err);
            assertThat(code).isEqualTo(1);
            String body = Files.readString(json, StandardCharsets.UTF_8).trim();
            assertThat(body).contains("\"status\":\"RUNTIME_FAILURE\"").contains("\"runtimeCategory\":\"ARITHMETIC_ERROR\"").contains("\"file\":\"arith.sol\"");
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    public void missingRunJsonPathIsSilent() throws IOException {
        // Without --run-json the launcher behaves exactly as before, executing the program.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = runArgs(new String[]{}, "print(\"no-json\")\n", out, err);
        assertThat(code).isEqualTo(0);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("no-json");
    }

    /** A guest-shaped object exposing a stable runtime category. */
    public static final class Categorized {
        public String category = "ARITHMETIC_ERROR";
    }

    /** A guest-shaped object with members but no category member. */
    public static final class Uncategorized {
        public String other = "x";
    }

    @Test
    public void runtimeCategoryReadsTheGuestMemberAndFallsBackStructurally() {
        try (Context context = Context.newBuilder().allowAllAccess(true).build()) {
            assertThat(RunResultJson.runtimeCategory(context.asValue(new Categorized()))).isEqualTo("ARITHMETIC_ERROR");
            // A guest value that is not an object, an object without the member, and a null guest all
            // fall back to the structured default rather than failing.
            assertThat(RunResultJson.runtimeCategory(context.asValue(42L))).isEqualTo("OTHER_RUNTIME_ERROR");
            assertThat(RunResultJson.runtimeCategory(context.asValue(new Uncategorized()))).isEqualTo("OTHER_RUNTIME_ERROR");
            assertThat(RunResultJson.runtimeCategory(null)).isEqualTo("OTHER_RUNTIME_ERROR");
        }
    }

    @Test
    public void serializerClampsExitAndDefaultsRuntimeCategory() {
        assertThat(RunResultJson.normalExit(0)).isEqualTo("{\"phase\":\"execute\",\"status\":\"NORMAL_EXIT\",\"languageExit\":0}");
        // A language status is kept inside the 0..255 process status range used by the protocol.
        assertThat(RunResultJson.normalExit(300)).isEqualTo("{\"phase\":\"execute\",\"status\":\"NORMAL_EXIT\",\"languageExit\":44}");
        assertThat(RunResultJson.normalExit(-1)).isEqualTo("{\"phase\":\"execute\",\"status\":\"NORMAL_EXIT\",\"languageExit\":0}");
        assertThat(RunResultJson.internalFailure()).isEqualTo("{\"phase\":\"execute\",\"status\":\"IMPLEMENTATION_FAILURE\"}");
    }
}
