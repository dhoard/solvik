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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.source.SourceSpan;

/**
 * One negative fixture per file under {@code language/tests/diagnostics/}. Each fixture starts with
 * a {@code // expected: <CODE>} comment naming the stable code the program must reject with; the
 * test runs the fixture end to end through a {@code solvik} context and asserts the first emitted
 * diagnostic's code equals it and that its span lies within the source. This replaces the two
 * coarse golden loops' negative path with a per-code harness; see TEST-COVERAGE.md slice 2.
 */
public final class SolvikDiagnosticProgramTest {

    private static final Pattern CODE = Pattern.compile("SOLV-[A-Z0-9-]+");

    private static Path diagnosticsDirectory() {
        Path direct = Path.of("tests", "diagnostics");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        return Path.of("language", "tests", "diagnostics");
    }

    @TestFactory
    public Stream<DynamicTest> everyDiagnosticFixtureReportsItsExpectedCode() throws IOException {
        Path directory = diagnosticsDirectory();
        assertThat(Files.isDirectory(directory)).as("diagnostics directory must exist: " + directory).isTrue();
        List<Path> fixtures;
        try (Stream<Path> files = Files.list(directory)) {
            fixtures = files.filter(path -> path.getFileName().toString().endsWith(".sol")).sorted().toList();
        }
        assertThat(fixtures.size() >= 1).as("at least one diagnostic fixture is required").isTrue();
        Stream.Builder<DynamicTest> builder = Stream.builder();
        for (Path fixture : fixtures) {
            String fileName = fixture.getFileName().toString();
            String source = text(fixture);
            String expected = expectedCode(source);
            Source sourceForRun = readSource(fixture);
            PolyglotException failure = runExpectingFailure(sourceForRun);
            builder.add(DynamicTest.dynamicTest("diagnostic: " + fileName, () -> {
                assertThat(expected).as("fixture must declare an expected code in " + fileName).isNotNull();
                DiagnosticCode actual = firstCode(failure);
                assertThat(actual.stableCode()).as(fileName).isEqualTo(expected);
                SourceSection section = failure.getSourceLocation();
                assertThat(section.hasCharIndex()).as("span must be available: " + fileName).isTrue();
                int start = section.getCharIndex();
                int end = section.getCharEndIndex();
                assertThat(start >= 0 && end <= source.length()).as(
                        "span " + start + ".." + end + " must lie within " + fileName)
                        .isTrue();
            }));
        }
        return builder.build();
    }

    private static String expectedCode(String text) {
        String header = text.lines().findFirst().orElse("");
        Matcher matcher = CODE.matcher(header);
        if (matcher.find() && header.trim().startsWith("// expected:")) {
            return matcher.group();
        }
        return null;
    }

    private static String text(Path fixture) {
        try {
            return Files.readString(fixture, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static final java.util.Map<String, DiagnosticCode> BY_CODE;

    static {
        java.util.Map<String, DiagnosticCode> map = new java.util.LinkedHashMap<>();
        for (DiagnosticCode code : DiagnosticCode.values()) {
            map.put(code.stableCode(), code);
        }
        BY_CODE = java.util.Collections.unmodifiableMap(map);
    }

    private static DiagnosticCode firstCode(PolyglotException failure) {
        Matcher matcher = CODE.matcher(failure.getMessage());
        assertThat(matcher.find()).as("no solvik code in " + failure.getMessage()).isTrue();
        DiagnosticCode code = BY_CODE.get(matcher.group());
        if (code == null) {
            throw new AssertionError("not a diagnostic code: " + matcher.group());
        }
        return code;
    }

    private static Source readSource(Path fixture) {
        try {
            return Source.newBuilder("solvik", fixture.toFile()).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static PolyglotException runExpectingFailure(Source source) {
        PolyglotException failure = null;
        try (Context context = Context.newBuilder("solvik")
                .out(new ByteArrayOutputStream())
                .err(new ByteArrayOutputStream())
                .allowAllAccess(true)
                .build()) {
            context.eval(source);
        } catch (PolyglotException e) {
            failure = e;
        }
        assertThat(failure).as("fixture must be rejected").isNotNull();
        return failure;
    }
}
