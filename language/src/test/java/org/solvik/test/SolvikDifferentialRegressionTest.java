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
import org.junit.jupiter.api.Test;

/**
 * Differential regression coverage for the {@code Any}-root hierarchy migration.
 *
 * <p>Every program in {@code language/tests/regression} was executed against the pre-change
 * baseline revision, and each successful program's output was captured as its golden file. A
 * program with a golden must still succeed and produce byte-identical output; a program without a
 * golden was rejected by the baseline and must still be rejected with no output. The programs
 * deliberately avoid the removed {@code Object} built-in, so any drift in unrelated language
 * behavior fails this suite instead of being attributed to the intentional hierarchy change.
 */
public final class SolvikDifferentialRegressionTest {

    private static Path regressionDirectory() {
        Path direct = Path.of("tests", "regression");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        return Path.of("language", "tests", "regression");
    }

    @Test
    public void baselineProgramsKeepTheirOutcomeAndOutput() throws IOException {
        Path directory = regressionDirectory();
        assertThat(Files.isDirectory(directory)).as("regression directory must exist: " + directory).isTrue();
        List<Path> programs;
        try (Stream<Path> files = Files.list(directory)) {
            programs = files.filter(path -> path.getFileName().toString().endsWith(".sol")).sorted().toList();
        }
        assertThat(programs.size() >= 60).as("the differential corpus must not shrink silently").isTrue();
        for (Path program : programs) {
            String fileName = program.getFileName().toString();
            String stem = fileName.substring(0, fileName.length() - ".sol".length());
            Path golden = program.resolveSibling(stem + ".output");
            if (Files.isRegularFile(golden)) {
                String expected = Files.readString(golden, StandardCharsets.UTF_8);
                String actual = runExpectingSuccess(readSource(program), fileName);
                assertThat(actual).as("output of " + fileName + " must match the baseline golden").isEqualTo(expected);
            } else {
                runExpectingRejection(readSource(program), fileName);
            }
        }
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

    private static void runExpectingRejection(Source source, String name) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PolyglotException failure = null;
        try (Context context = context(out)) {
            context.eval(source);
        } catch (PolyglotException e) {
            failure = e;
        }
        assertThat(failure).as(name + " was rejected by the baseline and must still be rejected").isNotNull();
        assertThat(out.toString(StandardCharsets.UTF_8)).as(name + " must produce no output when rejected").isEmpty();
    }

    private static Context context(ByteArrayOutputStream out) {
        return Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build();
    }
}
