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

import java.io.IOException;
import java.io.UncheckedIOException;
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
 * <p>Two of the codes ({@link DiagnosticCode#RESOL_INCLUDE_NOT_FILE} and
 * {@link DiagnosticCode#RESOL_INCLUDE_IO}) are gated on filesystem conditions that the JDK's
 * public-file-access policy reports as absent (a directory or a special file is reported as not
 * found; an unreadable file is likewise reported as not found), so they cannot be driven through
 * the context on this platform. They are documented and gated in
 * {@link SolvikDiagnosticCodeCoverageTest}.
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
