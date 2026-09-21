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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.graalvm.polyglot.io.IOAccess;
import org.junit.jupiter.api.Test;

/**
 * End-to-end include execution, diagnostic location, and host-policy coverage through the polyglot
 * engine and real temporary files.
 */
public final class SolvikIncludeExecutionTest {

    private static final class Run {
        final String output;
        final PolyglotException failure;

        Run(String output, PolyglotException failure) {
            this.output = output;
            this.failure = failure;
        }
    }

    private static Path tempDir() throws IOException {
        return Files.createTempDirectory("solvik-include");
    }

    private static Path write(Path directory, String name, String content) throws IOException {
        Path file = directory.resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
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

    private static Run eval(Source source, Path workingDirectory, IOAccess ioAccess) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotException failure = null;
        Context.Builder builder = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true);
        if (ioAccess != null) {
            builder.allowIO(ioAccess);
        }
        if (workingDirectory != null) {
            builder.currentWorkingDirectory(workingDirectory);
        }
        try (Context context = builder.build()) {
            context.eval(source);
        } catch (PolyglotException e) {
            failure = e;
        }
        return new Run(out.toString(StandardCharsets.UTF_8), failure);
    }

    private static Source fileSource(Path file) {
        try {
            return Source.newBuilder("solvik", file.toFile()).build();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static Source memorySource(String text, String name) {
        try {
            return Source.newBuilder("solvik", text, name).build();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    public void mergesTopLevelStatementsInDepthFirstOrder() throws IOException {
        Path dir = tempDir();
        try {
            write(dir, "a.sol", "println(\"a1\")\ninclude \"b.sol\"\nprintln(\"a2\")\n");
            write(dir, "b.sol", "println(\"b1\")\n");
            Path root = write(dir, "root.sol", "include \"a.sol\"\nprintln(\"root\")\n");
            Run run = eval(fileSource(root), null, null);
            assertThat(run.failure).isNull();
            assertThat(run.output).isEqualTo("a1\nb1\na2\nroot\n");
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void includedFunctionExecutes() throws IOException {
        Path dir = tempDir();
        try {
            write(dir, "lib.sol", "func twice(x: Integer): Integer {\n    return x * 2\n}\n");
            Path root = write(dir, "root.sol", "include \"lib.sol\"\nprintln(twice(21))\n");
            Run run = eval(fileSource(root), null, null);
            assertThat(run.failure).isNull();
            assertThat(run.output).isEqualTo("42\n");
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void sharedTopLevelStatementRunsOnce() throws IOException {
        Path dir = tempDir();
        try {
            write(dir, "common.sol", "println(\"common\")\n");
            write(dir, "a.sol", "include \"common.sol\"\nprintln(\"a\")\n");
            write(dir, "b.sol", "include \"common.sol\"\nprintln(\"b\")\n");
            Path root = write(dir, "root.sol", "include \"a.sol\"\ninclude \"b.sol\"\n");
            Run run = eval(fileSource(root), null, null);
            assertThat(run.failure).isNull();
            assertThat(run.output).isEqualTo("common\na\nb\n");
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void declarationOnlyGraphDoesNothing() throws IOException {
        Path dir = tempDir();
        try {
            write(dir, "lib.sol", "func helper(): Integer {\n    return 1\n}\n");
            Path root = write(dir, "root.sol", "include \"lib.sol\"\n");
            Run run = eval(fileSource(root), null, null);
            assertThat(run.failure).isNull();
            assertThat(run.output).isEqualTo("");
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void absoluteAndRawStringPathsLoad() throws IOException {
        Path dir = tempDir();
        try {
            Path lib = write(dir, "lib.sol", "func helper(): Integer {\n    return 7\n}\n");
            Path root = write(dir, "root.sol", "include " + quote(lib.toString()) + "\nprintln(helper())\n");
            assertThat(eval(fileSource(root), null, null).output).isEqualTo("7\n");
            Path rawRoot = write(dir, "raw.sol", "include r#\"" + lib.toString() + "\"#\nprintln(helper())\n");
            assertThat(eval(fileSource(rawRoot), null, null).output).isEqualTo("7\n");
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void relativeIncludeFromStdinUsesWorkingDirectory() throws IOException {
        Path dir = tempDir();
        try {
            write(dir, "lib.sol", "func helper(): Integer {\n    return 5\n}\n");
            Run run = eval(memorySource("include \"lib.sol\"\nprintln(helper())\n", "<stdin>"), dir, null);
            assertThat(run.failure).isNull();
            assertThat(run.output).isEqualTo("5\n");
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void leadingHomeExpansionResolvesAgainstProcessHome() throws IOException {
        Path home = tempDir();
        String previous = System.getProperty("user.home");
        try {
            write(home, "lib.sol", "func helper(): Integer {\n    return 3\n}\n");
            Path work = tempDir();
            try {
                Path root = write(work, "root.sol", "include \"~/lib.sol\"\nprintln(helper())\n");
                System.setProperty("user.home", home.toString());
                Run run = eval(fileSource(root), null, null);
                assertThat(run.failure).isNull();
                assertThat(run.output).isEqualTo("3\n");
            } finally {
                deleteRecursively(work);
            }
        } finally {
            if (previous == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", previous);
            }
            deleteRecursively(home);
        }
    }

    @Test
    public void missingHomeReportsInvalidPath() throws IOException {
        Path dir = tempDir();
        String previous = System.getProperty("user.home");
        try {
            Path root = write(dir, "root.sol", "include \"~/lib.sol\"\n");
            System.clearProperty("user.home");
            Run run = eval(fileSource(root), null, null);
            assertThat(run.failure).isNotNull();
            assertThat(run.failure.getMessage().contains("SOLV-RESOL-007")).as(run.failure.getMessage()).isTrue();
        } finally {
            if (previous != null) {
                System.setProperty("user.home", previous);
            }
            deleteRecursively(dir);
        }
    }

    @Test
    public void tildeNameIsOrdinaryPathText() throws IOException {
        Path dir = tempDir();
        try {
            Path root = write(dir, "root.sol", "include \"~nobody/lib.sol\"\n");
            Run run = eval(fileSource(root), null, null);
            assertThat(run.failure).isNotNull();
            assertThat(run.failure.getMessage().contains("SOLV-RESOL-008")).as(run.failure.getMessage()).isTrue();
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void missingIncludeReportsNotFound() throws IOException {
        Path dir = tempDir();
        try {
            Path root = write(dir, "root.sol", "include \"missing.sol\"\n");
            Run run = eval(fileSource(root), null, null);
            assertThat(run.failure).isNotNull();
            assertThat(run.failure.getMessage().contains("SOLV-RESOL-008")).as(run.failure.getMessage()).isTrue();
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void directoryTargetReportsNotFile() throws IOException {
        Path dir = tempDir();
        try {
            Files.createDirectories(dir.resolve("adir.sol"));
            Path root = write(dir, "root.sol", "include \"adir.sol\"\n");
            Run run = eval(fileSource(root), null, null);
            assertThat(run.failure).isNotNull();
            assertThat(run.failure.getMessage().contains("SOLV-RESOL-009")).as(run.failure.getMessage()).isTrue();
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void noIncludeRootRunsWithIoDenied() throws IOException {
        Path dir = tempDir();
        try {
            Path root = write(dir, "root.sol", "println(\"no io\")\n");
            Run run = eval(fileSource(root), null, IOAccess.NONE);
            assertThat(run.failure).isNull();
            assertThat(run.output).isEqualTo("no io\n");
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void inMemoryNoIncludeRootRunsWithIoDenied() {
        Run run = eval(memorySource("println(\"memory\")\n", "mem.sol"), null, IOAccess.NONE);
        assertThat(run.failure).isNull();
        assertThat(run.output).isEqualTo("memory\n");
    }

    @Test
    public void deniedIncludeIoReportsIoDiagnostic() throws IOException {
        Path dir = tempDir();
        try {
            Path lib = write(dir, "lib.sol", "println(\"lib\")\n");
            // IOAccess.NONE forbids setting a working directory, so this in-memory root uses an
            // absolute include path; every public file operation for it must be denied.
            Run run = eval(memorySource("include " + quote(lib.toAbsolutePath().toString()) + "\n", "root.sol"), null, IOAccess.NONE);
            assertThat(run.failure).isNotNull();
            assertThat(run.failure.getMessage().contains("SOLV-RESOL-010")).as(run.failure.getMessage()).isTrue();
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void includedParseErrorReportsIncludedFileAndLocation() throws IOException {
        Path dir = tempDir();
        try {
            write(dir, "bad.sol", "func broken(\n");
            Path root = write(dir, "root.sol", "include \"bad.sol\"\n");
            Run run = eval(fileSource(root), null, null);
            assertThat(run.failure).isNotNull();
            assertThat(run.failure.getMessage().contains("bad.sol")).as(run.failure.getMessage()).isTrue();
            SourceSection location = run.failure.getSourceLocation();
            assertThat(location).isNotNull();
            assertThat(location.getSource().getName().contains("bad.sol")).as(location.getSource().getName()).isTrue();
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void includedSemanticErrorReportsIncludedFileAndLocation() throws IOException {
        Path dir = tempDir();
        try {
            write(dir, "bad.sol", "func broken(): Integer {\n    return \"no\"\n}\n");
            Path root = write(dir, "root.sol", "include \"bad.sol\"\n");
            Run run = eval(fileSource(root), null, null);
            assertThat(run.failure).isNotNull();
            assertThat(run.failure.getMessage().contains("SOLV-TYPE-009")).as(run.failure.getMessage()).isTrue();
            assertThat(run.failure.getMessage().contains("bad.sol")).as(run.failure.getMessage()).isTrue();
            SourceSection location = run.failure.getSourceLocation();
            assertThat(location).isNotNull();
            assertThat(location.getSource().getName().contains("bad.sol")).as(location.getSource().getName()).isTrue();
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void runtimeErrorInIncludedFunctionPointsAtIncludedFile() throws IOException {
        Path dir = tempDir();
        try {
            write(dir, "bad.sol", "func boom(): Integer {\n    val x: Integer = 1\n    return x / 0\n}\n");
            Path root = write(dir, "root.sol", "include \"bad.sol\"\nprintln(boom())\n");
            Run run = eval(fileSource(root), null, null);
            assertThat(run.failure).isNotNull();
            assertThat(run.failure.isSyntaxError()).isFalse();
            assertThat(run.failure.getMessage().contains("division by zero")).as(run.failure.getMessage()).isTrue();
            SourceSection location = run.failure.getSourceLocation();
            assertThat(location).isNotNull();
            assertThat(location.getSource().getName().contains("bad.sol")).as(location.getSource().getName()).isTrue();
        } finally {
            deleteRecursively(dir);
        }
    }

    @Test
    public void selfCycleFailsWithIncludeCycleCode() throws IOException {
        Path dir = tempDir();
        try {
            Path root = write(dir, "root.sol", "include \"root.sol\"\n");
            Run run = eval(fileSource(root), null, null);
            assertThat(run.failure).isNotNull();
            assertThat(run.failure.getMessage().contains("SOLV-RESOL-011")).as(run.failure.getMessage()).isTrue();
        } finally {
            deleteRecursively(dir);
        }
    }

    private static String quote(String path) {
        return "\"" + path.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
