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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expressed or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.solvik.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import org.solvik.diagnostic.DiagnosticCode;

/**
 * Context-level coverage of the include file-access boundary. Includes resolve against the real
 * filesystem through the Truffle backend, so these exercise the {@code RESOL_INCLUDE_*} failure
 * codes raised while loading an included {@code .sol} program.
 *
 * <p>{@link DiagnosticCode#RESOL_INCLUDE_NOT_FILE} is driven by an included path that exists but is
 * a directory whose name ends in {@code .sol} (a name without the extension is rejected earlier as
 * {@link DiagnosticCode#RESOL_INCLUDE_INVALID_PATH}, which is the specified path rule). {@link
 * DiagnosticCode#RESOL_INCLUDE_IO} is driven by an environment without file access, which is the
 * portable way to reach a denied read: filesystem permission bits are meaningless for a root user and
 * unreliable on Windows. Both codes are asserted exactly, as {@code docs/LANGUAGE_SPEC.md} section 20
 * requires (denied access and other I/O failures become {@code SOLV-RESOL-010} and never escape as
 * host errors).
 */
public final class SolvikIncludeAccessTest {

    private static final String ID = "solvik-include-access";

    private static Path scratch() throws IOException {
        return Files.createTempDirectory(ID);
    }

    @TestFactory
    public Stream<DynamicTest> includingAPathWithoutTheSolExtensionReportsInvalidPath() throws IOException {
        Path program = scratch().resolve("include-bad.sol");
        Files.writeString(program, "include \"missing\"\nfunc f(): Unit {\n}\n");
        assertThat(codeContains(runExpectingFailure(program), DiagnosticCode.RESOL_INCLUDE_INVALID_PATH)).isTrue();
        return Stream.of(DynamicTest.dynamicTest("include without .sol -> invalid-path", () -> {
            assertThat(codeContains(runExpectingFailure(program), DiagnosticCode.RESOL_INCLUDE_INVALID_PATH)).isTrue();
        }));
    }

    @TestFactory
    public Stream<DynamicTest> includingAFileThatDoesNotExistReportsNotFound() throws IOException {
        Path program = scratch().resolve("include-missing.sol");
        Files.writeString(program, "include \"does-not-exist.sol\"\nfunc f(): Unit {\n}\n");
        assertThat(codeContains(runExpectingFailure(program), DiagnosticCode.RESOL_INCLUDE_NOT_FOUND)).isTrue();
        return Stream.of(DynamicTest.dynamicTest("missing include -> not-found", () -> {
            assertThat(codeContains(runExpectingFailure(program), DiagnosticCode.RESOL_INCLUDE_NOT_FOUND)).isTrue();
        }));
    }

    private static boolean codeContains(PolyglotException failure, DiagnosticCode code) {
        Matcher matcher = Pattern.compile("SOLV-[A-Z0-9-]+").matcher(failure.getMessage());
        boolean found = false;
        while (matcher.find()) {
            if (matcher.group().equals(code.stableCode())) {
                found = true;
                break;
            }
        }
        assertThat(found).as("message must mention %s: %s".formatted(code.stableCode(), failure.getMessage())).isTrue();
        return found;
    }

    /**
     * An included path that exists but is not a regular file is {@code RESOL_INCLUDE_NOT_FILE}. The
     * directory must be named {@code *.sol}: a name without the extension is rejected earlier as
     * {@code RESOL_INCLUDE_INVALID_PATH} by the path rule of section 20.
     */
    @TestFactory
    public Stream<DynamicTest> includingADirectoryReportsNotAFile() throws IOException {
        Path directory = scratch().resolve("not-a-file.sol");
        Files.createDirectory(directory);
        Path program = scratch().resolve("include-directory.sol");
        Files.writeString(program, "include \"" + literalPath(directory) + "\"\n\nfunc f(): Unit {\n}\n");
        return Stream.of(DynamicTest.dynamicTest("include directory -> not-a-file", () -> {
            assertThat(codeContains(runExpectingFailure(program), DiagnosticCode.RESOL_INCLUDE_NOT_FILE)).isTrue();
        }));
    }

    /**
     * A denied read is {@code SOLV-RESOL-010} and never a host error. The environment has no file
     * access, so the failure is about denial rather than about the path: the same program resolves
     * successfully in a permitted environment.
     */
    @TestFactory
    public Stream<DynamicTest> includingWithoutFileAccessReportsIOError() throws IOException {
        Path target = scratch().resolve("neighbor.sol");
        Files.writeString(target, "func g(): Unit {\n}\n");
        Path program = scratch().resolve("include-denied.sol");
        Files.writeString(program, "include \"" + literalPath(target) + "\"\n\nfunc f(): Unit {\n}\n");
        return Stream.of(DynamicTest.dynamicTest("include without file access -> io-error", () -> {
            assertThat(codeContains(runExpectingFailureWithoutFileAccess(program), DiagnosticCode.RESOL_INCLUDE_IO)).isTrue();
        }));
    }

    /** The same absolute include that is denied above resolves when the environment permits file access. */
    @TestFactory
    public Stream<DynamicTest> includingAnExistingFileWithFileAccessResolves() throws IOException {
        Path target = scratch().resolve("resolvable.sol");
        Files.writeString(target, "func g(): Integer {\n    return 41\n}\n");
        Path program = scratch().resolve("include-allowed.sol");
        Files.writeString(program, "include \"" + literalPath(target) + "\"\n\nprintln(g() + 1)\n");
        return Stream.of(DynamicTest.dynamicTest("include with file access -> resolves", () -> {
            assertThat(run(program)).isEqualTo("42\n");
        }));
    }

    private static String literalPath(Path path) {
        return path.toString().replace("\\", "\\\\");
    }

    private static String run(Path program) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Source source;
        try {
            source = Source.newBuilder("solvik", read(program), program.toString()).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(source);
        } catch (PolyglotException e) {
            throw new AssertionError("program must run: " + e.getMessage(), e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static PolyglotException runExpectingFailureWithoutFileAccess(Path program) {
        Source source;
        try {
            source = Source.newBuilder("solvik", read(program), program.toString()).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        PolyglotException failure = null;
        try (Context context = Context.newBuilder("solvik").allowAllAccess(false).allowIO(false).build()) {
            context.eval(source);
        } catch (PolyglotException e) {
            failure = e;
        } catch (RuntimeException e) {
            // An environment that cannot even be built, or that raises outside the guest, must still
            // be reported as a failure rather than silently accepted.
            throw new AssertionError("include denial must surface as a guest failure: " + e, e);
        }
        assertThat(failure).as("program must be rejected").isNotNull();
        return failure;
    }

    private static PolyglotException runExpectingFailure(Path program) {
        String text = read(program);
        Source source;
        try {
            source = Source.newBuilder("solvik", text, program.toString()).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        PolyglotException failure = null;
        try (Context context = Context.newBuilder("solvik")
                .out(new java.io.ByteArrayOutputStream())
                .err(new java.io.ByteArrayOutputStream())
                .allowAllAccess(true)
                .build()) {
            context.eval(source);
        } catch (PolyglotException e) {
            failure = e;
        }
        assertThat(failure).as("program must be rejected").isNotNull();
        return failure;
    }

    private static String read(Path program) {
        try {
            return Files.readString(program);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
