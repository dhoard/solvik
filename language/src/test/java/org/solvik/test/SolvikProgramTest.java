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
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;

/**
 * Release-syntax examples: every checked-in {@code language/tests/*.sol} example runs end to end
 * through the {@code solvik} language and produces exactly its {@code .output} golden file. Each
 * example is its own {@code DynamicTest} named by file, so one mismatch reports a single file and
 * leaves the rest runnable. The examples are the published syntax surface, not migration fixtures.
 *
 * <p>This suite replaces the single-loop {@code SolvikExamplesTest}; see TEST-COVERAGE.md slice 1.
 */
public final class SolvikProgramTest {

    private static Path testDirectory() {
        Path direct = Path.of("tests");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        return Path.of("language", "tests");
    }

    @TestFactory
    public Stream<DynamicTest> everyExampleRunsAndMatchesItsGoldenOutput() throws IOException {
        Path directory = testDirectory();
        assertThat(Files.isDirectory(directory)).as("example directory must exist: " + directory).isTrue();
        List<Path> examples;
        try (Stream<Path> files = Files.list(directory)) {
            examples = files.filter(path -> path.getFileName().toString().endsWith(".sol")).sorted().toList();
        }
        assertThat(examples.size() >= 4).as("at least four Solvik examples are required").isTrue();
        Stream.Builder<DynamicTest> builder = Stream.builder();
        for (Path example : examples) {
            String fileName = example.getFileName().toString();
            Path golden = example.resolveSibling(fileName.substring(0, fileName.length() - ".sol".length()) + ".output");
            builder.add(DynamicTest.dynamicTest("example: " + fileName, () -> {
                assertThat(Files.isRegularFile(golden))
                        .as(fileName + " must have a golden " + golden.getFileName())
                        .isTrue();
                String expected = Files.readString(golden, StandardCharsets.UTF_8);
                String actual = run(readSource(example), fileName);
                assertThat(actual).as("output of " + fileName).isEqualTo(expected);
            }));
        }
        return builder.build();
    }

    private static Source readSource(Path example) {
        try {
            // Build the source from the real file so a relative include has a real base directory.
            return Source.newBuilder("solvik", example.toFile()).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String run(Source source, String name) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(source);
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
