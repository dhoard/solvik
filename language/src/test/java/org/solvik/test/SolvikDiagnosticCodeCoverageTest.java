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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.solvik.diagnostic.DiagnosticCode;

/**
 * Guards against silent diagnostic gaps. Every {@code DiagnosticCode} constant must be reachable by a
 * test: referenced in a test source file (the per-feature {@code Solvik*NegativeTest} suites), named
 * by a fixture under {@code language/tests/diagnostics/}, or explicitly allow-listed here with a
 * documented reason. Adding a code without a matching test fails this meta-test. See
 * TEST-COVERAGE.md slice 2.
 *
 * <p>The allow-list contains only codes unreachable through any valid Solvik program in its current
 * grammar. {@code TYPE_INVALID_CHARACTER_LITERAL} can never fire because the grammar admits only
 * single-character or single-escape literals, and {@code TYPE_UNINITIALIZED_VARIABLE} never fires for
 * a local because every binding marks itself initialized at declaration; both are covered by unit
 * tests rather than fixtures.
 */
public final class SolvikDiagnosticCodeCoverageTest {

    private static final Pattern CODE = Pattern.compile("SOLV-[A-Z0-9-]+");

    /**
     * Codes unreachable through any valid Solvik program in its current grammar, so they cannot be
     * driven through a {@code solvik} context or a fixture under {@code tests/diagnostics/}. They
     * are covered by context-level tests that document the platform limitation.
     *
     * <ul>
     *   <li>{@link DiagnosticCode#RESOL_INCLUDE_NOT_FILE} &ndash; fires when the resolved path exists
     *       but is not a regular file. The JDK public-file-access policy reports a directory (or any
     *       non-regular file) as not found, so this branch cannot be exercised through the context.</li>
     *   <li>{@link DiagnosticCode#RESOL_INCLUDE_IO} &ndash; fires when reading the resolved file is
     *       denied or fails. The default environment grants unrestricted file access and reports an
     *       unreadable file as not found, so this branch cannot be exercised through the context.</li>
     * </ul>
     */
    private static final Set<DiagnosticCode> ALLOW_LIST = new LinkedHashSet<>();

    static {
        ALLOW_LIST.add(DiagnosticCode.RESOL_INCLUDE_NOT_FILE);
        ALLOW_LIST.add(DiagnosticCode.RESOL_INCLUDE_IO);
    }

    @Test
    public void everyDiagnosticCodeIsCoveredByATest() throws IOException {
        Set<DiagnosticCode> covered = new LinkedHashSet<>();
        covered.addAll(referencedInTestSources());
        covered.addAll(fixtureCodes());
        for (DiagnosticCode code : DiagnosticCode.values()) {
            assertThat(covered.contains(code) || ALLOW_LIST.contains(code))
                    .as("no test covers " + code + "; add a fixture under tests/diagnostics/, reference it, or allow-list it")
                    .isTrue();
        }
    }

    /**
     * Resolves a fixture directory. Tests may run from either the module root (where the layout is
     * {@code tests/diagnostics}) or the repository root (where it is {@code language/tests/diagnostics}).
     */
    private static Path diagnosticsDirectory() {
        Path[][] candidates = new Path[][]{
                new Path[]{Path.of("tests", "diagnostics")},
                new Path[]{Path.of("language", "tests", "diagnostics")}};
        for (Path path : flatten(candidates)) {
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        throw new IllegalStateException("diagnostics directory not found");
    }

    /**
     * Resolves the directory holding the language test sources, checking both the module root and the
     * repository root. Returns {@code null} when neither exists.
     */
    private static Path testSourcesRoot() {
        Path[][] candidates = new Path[][]{
                new Path[]{Path.of("src", "test")},
                new Path[]{Path.of("language", "src", "test")}};
        for (Path path : flatten(candidates)) {
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return null;
    }

    /** Every {@code DiagnosticCode.NAME} constant referenced in the language test sources. */
    private static Set<DiagnosticCode> referencedInTestSources() throws IOException {
        Set<DiagnosticCode> referenced = new LinkedHashSet<>();
        Path root = testSourcesRoot();
        if (root == null) {
            return referenced;
        }
        for (Path file : Files.walk(root).filter(Files::isRegularFile).toList()) {
            if (file.getFileName().toString().equals("DiagnosticCode.java")) {
                continue;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            for (String name : diagnosticNames()) {
                if (Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(text).find()) {
                    referenced.add(DiagnosticCode.valueOf(name));
                }
            }
        }
        return referenced;
    }

    /** The expected code declared by each fixture under {@code tests/diagnostics/}. */
    private static Set<DiagnosticCode> fixtureCodes() throws IOException {
        Set<DiagnosticCode> codes = new LinkedHashSet<>();
        Path directory = diagnosticsDirectory();
        for (Path file : Files.list(directory)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".sol"))
                .toList()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String header = text.lines().findFirst().orElse("");
            if (header.trim().startsWith("// expected:")) {
                Matcher matcher = CODE.matcher(header);
                if (matcher.find()) {
                    codes.add(byCode(matcher.group()));
                }
            }
        }
        return codes;
    }

    /** Maps a stable code string (e.g. {@code SOLV-LEX-001}) to its {@link DiagnosticCode} constant. */
    private static DiagnosticCode byCode(String stableCode) {
        for (DiagnosticCode code : DiagnosticCode.values()) {
            if (code.stableCode().equals(stableCode)) {
                return code;
            }
        }
        throw new AssertionError("unknown diagnostic code: " + stableCode);
    }

    /** Resolves the {@code DiagnosticCode.java} enum source from the module root or repository root. */
    private static Path findEnumSource() throws IOException {
        Path[][] candidates = new Path[][]{
                new Path[]{Path.of("src", "main", "java", "org", "solvik", "diagnostic", "DiagnosticCode.java")},
                new Path[]{Path.of("language", "src", "main", "java", "org", "solvik", "diagnostic", "DiagnosticCode.java")}};
        for (Path path : flatten(candidates)) {
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        throw new IllegalStateException("DiagnosticCode.java source not found");
    }

    /** Flattens a {@code Path[][]} of candidate locations into a single iterable list. */
    private static List<Path> flatten(Path[][] candidates) {
        List<Path> paths = new ArrayList<>();
        for (Path[] group : candidates) {
            for (Path path : group) {
                paths.add(path);
            }
        }
        return paths;
    }

    /** The constant names declared in {@code DiagnosticCode}, parsed from the enum body. */
    private static List<String> diagnosticNames() throws IOException {
        Path file = findEnumSource();
        String src = Files.readString(file);
        Matcher matcher = Pattern.compile("\\b([A-Z][A-Z0-9_-]+)\\(\"[A-Z0-9-]+\"").matcher(src);
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
