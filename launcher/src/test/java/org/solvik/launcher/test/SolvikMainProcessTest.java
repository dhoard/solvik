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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Process-level launcher tests. The command-line surface - file arguments, option parsing with and
 * without {@code =}, and reading the program from standard input - is only observable when the
 * launcher's {@code main} actually runs, so each case starts the real launcher module in a child
 * JVM and asserts the process exit status and output.
 */
public final class SolvikMainProcessTest {

    private record Result(int exitCode, String out, String err) {
    }

    private static Result run(String stdin, String... arguments) throws IOException, InterruptedException {
        String modulePath = System.getProperty("jdk.module.path");
        Assumptions.assumeTrue(modulePath != null && !modulePath.isEmpty(), "the launcher must run on the module path");

        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("--module-path");
        command.add(modulePath);
        command.add("--add-modules");
        command.add("ALL-MODULE-PATH");
        command.add("--enable-native-access=org.graalvm.truffle");
        command.add("--sun-misc-unsafe-memory-access=allow");
        command.add("-m");
        command.add("org.solvik.launcher/org.solvik.launcher.SolvikMain");
        command.addAll(List.of(arguments));

        Process process = new ProcessBuilder(command).start();
        try (OutputStream input = process.getOutputStream()) {
            if (stdin != null) {
                input.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        }
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        return new Result(exitCode, out, err);
    }

    @Test
    public void fileArgumentRunsTheProgram(@TempDir Path directory) throws IOException, InterruptedException {
        Path source = directory.resolve("program.sol");
        Files.writeString(source, "println(\"from-file\")\n", StandardCharsets.UTF_8);
        Result result = run(null, source.toString());
        assertThat(result.exitCode).isEqualTo(0);
        assertThat(result.out).isEqualTo("from-file\n");
        assertThat(result.err).isEqualTo("");
    }

    @Test
    public void optionWithoutEqualsIsAcceptedBeforeTheFile(@TempDir Path directory) throws IOException, InterruptedException {
        Path source = directory.resolve("program.sol");
        Files.writeString(source, "println(\"option\")\n", StandardCharsets.UTF_8);
        Result result = run(null, "--engine.WarnInterpreterOnly=false", source.toString());
        assertThat(result.exitCode).isEqualTo(0);
        assertThat(result.out).isEqualTo("option\n");
    }

    @Test
    public void optionWithEqualsReachesTheEngine(@TempDir Path directory) throws IOException, InterruptedException {
        Path source = directory.resolve("program.sol");
        Files.writeString(source, "println(\"option-with-equals\")\n", StandardCharsets.UTF_8);
        Result result = run(null, "--engine.WarnInterpreterOnly=true", source.toString());
        assertThat(result.exitCode).isEqualTo(0);
        assertThat(result.out).isEqualTo("option-with-equals\n");
    }

    @Test
    public void unknownOptionFailsWithStatusOne(@TempDir Path directory) throws IOException, InterruptedException {
        Path source = directory.resolve("program.sol");
        Files.writeString(source, "println(\"never\")\n", StandardCharsets.UTF_8);
        Result result = run(null, "--solvik.noSuchOption=true", source.toString());
        assertThat(result.exitCode).isEqualTo(1);
        assertThat(result.out).isEqualTo("");
        assertThat(result.err).contains("noSuchOption");
    }

    @Test
    public void programIsReadFromStandardInputWhenNoFileIsGiven() throws IOException, InterruptedException {
        Result result = run("println(\"from-stdin\")\n");
        assertThat(result.exitCode).isEqualTo(0);
        assertThat(result.out).isEqualTo("from-stdin\n");
        assertThat(result.err).isEqualTo("");
    }

    @Test
    public void missingSourceFileFailsWithoutExecuting() throws IOException, InterruptedException {
        Result result = run(null, "/definitely/not/a/solvik/program.sol");
        assertThat(result.exitCode).isNotEqualTo(0);
        assertThat(result.out).isEqualTo("");
    }
}
