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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.source.SourceFile;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * Differential-regression coverage for {@code language/tests/regression/*.sol}. A program with a
 * sibling {@code .output} must still succeed and produce byte-identical output; a program without
 * one was rejected by the baseline and must still be rejected with no output. When a
 * {@code <stem>.error} file is present, the first diagnostic's stable code must equal its contents.
 * Each program is its own {@code DynamicTest} named by file.
 *
 * <p>This suite replaces the single-loop {@code SolvikDifferentialRegressionTest}; see
 * TEST-COVERAGE.md slice 1.
 */
public final class SolvikRegressionProgramTest {

    private static Path regressionDirectory() {
        Path direct = Path.of("tests", "regression");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        return Path.of("language", "tests", "regression");
    }

    @TestFactory
    public Stream<DynamicTest> baselineProgramsKeepTheirOutcomeAndOutput() throws IOException {
        Path directory = regressionDirectory();
        assertThat(Files.isDirectory(directory)).as("regression directory must exist: " + directory).isTrue();
        List<Path> programs;
        try (Stream<Path> files = Files.list(directory)) {
            programs = files.filter(path -> path.getFileName().toString().endsWith(".sol")).sorted().toList();
        }
        assertThat(programs.size() >= 60).as("the differential corpus must not shrink silently").isTrue();
        Stream.Builder<DynamicTest> builder = Stream.builder();
        for (Path program : programs) {
            String fileName = program.getFileName().toString();
            String stem = fileName.substring(0, fileName.length() - ".sol".length());
            builder.add(DynamicTest.dynamicTest("regression: " + fileName, () -> {
                Path golden = program.resolveSibling(stem + ".output");
                if (Files.isRegularFile(golden)) {
                    String expected = Files.readString(golden, StandardCharsets.UTF_8);
                    String actual = runExpectingSuccess(readSource(program), fileName);
                    assertThat(actual).as("output of " + fileName + " must match the baseline golden")
                            .isEqualTo(expected);
                } else {
                    runExpectingRejection(program, fileName);
                }
            }));
        }
        return builder.build();
    }

    private static Source readSource(Path program) {
        try {
            return Source.newBuilder("solvik", program.toFile()).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String runExpectingSuccess(Source source, String name) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = context(out)) {
            context.eval(source);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static void runExpectingRejection(Path program, String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotException failure = null;
        try (Context context = context(out)) {
            context.eval(readSource(program));
        } catch (PolyglotException e) {
            failure = e;
        }
        assertThat(failure).as(name + " was rejected by the baseline and must still be rejected").isNotNull();
        assertThat(failure.isGuestException()).as(name + " must fail with a guest error").isTrue();
        assertThat(out.toString(StandardCharsets.UTF_8)).as(name + " must produce no output when rejected")
                .isEmpty();
        Path error = program.resolveSibling(stemOf(program) + ".error");
        if (Files.isRegularFile(error)) {
            assertThat(firstStableCode(Files.readString(program, StandardCharsets.UTF_8)))
                    .as(name + " must reject with the expected stable code " + error.getFileName())
                    .isEqualTo(Files.readString(error, StandardCharsets.UTF_8).trim());
        }
    }

    private static String stemOf(Path program) {
        return program.getFileName().toString().substring(0, program.getFileName().toString().length() - ".sol".length());
    }

    private static Context context(ByteArrayOutputStream out) {
        return Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build();
    }

    /**
     * Returns the stable code of the first diagnostic for the given source text, or {@code null} when
     * the program parses and type-checks without error. Used only for the optional {@code .error}
     * expectation; the outcome itself is asserted through the {@code solvik} context.
     */
    private static String firstStableCode(String text) {
        SolvikParseResult result = SolvikParser.parse(new SourceFile("regression.sol", text));
        if (!result.isSuccess()) {
            return first(result.diagnostics().all()).code().stableCode();
        }
        SemanticResult semantic = SolvikSemanticAnalyzer.analyze(result.requireAst());
        if (!semantic.isSuccess()) {
            return first(semantic.diagnostics().all()).code().stableCode();
        }
        return null;
    }

    private static Diagnostic first(java.util.List<Diagnostic> diagnostics) {
        assertThat(diagnostics.isEmpty()).isFalse();
        return diagnostics.get(0);
    }
}
