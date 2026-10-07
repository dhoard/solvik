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
 * <p><strong>Conventions for this file.</strong> Codes are written in prose by their stable string
 * ({@code SOLV-TYPE-008}), never by their enum constant name, because the scan below reads test sources
 * to decide what is covered and this file is one of them — a prose mention of a constant name would
 * certify that constant as tested. {@link #thisFileDoesNotCoverAnyDiagnosticCodeItself()} enforces the
 * rule, so the only constant names this file may contain are the allow-listed ones, which appear in the
 * {@code ALLOW_LIST} initializer and nowhere else.
 *
 * <p>The allow-list contains only codes unreachable through any valid Solvik program in its current
 * grammar, so neither can be driven through a {@code solvik} context. A code that becomes reachable must
 * have its entry dropped, which {@link #everyAllowListedCodeIsStillUncoveredElsewhere()} detects.
 *
 * <p>Two codes that look like candidates are deliberately <em>not</em> allow-listed:
 * <ul>
 *   <li>{@code SOLV-RESOL-009} and {@code SOLV-RESOL-010} (the include diagnostics) were previously
 *       allow-listed under the incorrect premise that the JDK public-file-access policy reports both
 *       conditions as not found. Both are reachable through a {@code solvik} context and are asserted by
 *       {@code SolvikIncludeAccessTest} (a {@code .sol}-named directory, and an environment without file
 *       access).</li>
 *   <li>{@code SOLV-TYPE-008} is genuinely reachable. No local can be read before its own
 *       initialization — every local declaration carries an initializer and marks its binding initialized
 *       — but a capture item naming the binding its own expression initializes is specified as exactly
 *       this error, and {@code SolvikCaptureTest}
 *       {@code aCaptureItemMayNotNameTheBindingBeingInitialized} drives it.</li>
 * </ul>
 */
public final class SolvikDiagnosticCodeCoverageTest {

    private static final Pattern CODE = Pattern.compile("SOLV-[A-Z0-9-]+");

    /**
     * Codes unreachable through any valid Solvik program in its current grammar, so they cannot be
     * driven through a {@code solvik} context or a fixture under {@code tests/diagnostics/}. Each entry
     * carries the reason here, since the reason is the whole content of the claim that no test can cover
     * the code, and a reader has to be able to check it. See the class javadoc for the two codes that
     * look like candidates and are deliberately not here.
     *
     * <p>{@code SOLV-SEM-059} is the capture-item diagnostic for a name that resolves to something other
     * than an eligible immutable local, parameter, or {@code this} ({@code docs/LANGUAGE_SPEC.md}
     * section 6). No program in the current grammar can reach it. A capture item is resolved by
     * {@code SymbolTable.resolveLocalChain}, which walks only the scopes from the current one up to, but
     * not into, the root scope, and the only symbols ever declared into those scopes are
     * {@code VariableSymbol}s: parameters, locals, {@code for-in} and pattern bindings, a catch binding,
     * and capture bindings themselves. Anything else a capture item could name — a top-level function,
     * class, enum, or interface — lives in the root scope the walk stops at, so the item resolves to
     * nothing and is reported as the unknown-name code ({@code SOLV-RESOL-001}) that the specification
     * explicitly assigns to an unknown capture item. The check is kept, and the code reserved, because a
     * later revision that declares a non-variable symbol into a function scope — a nested type or a local
     * alias would both do it — makes the branch live, and the analyzer should report the capture-specific
     * code then rather than silently binding the item. See also {@code
     * SolvikSemanticAnalyzer#resolveCaptures}, where the branch is annotated as unreachable.
     *
     * <p>{@code SOLV-TYPE-020} is the semantic check that a character literal holds exactly one
     * character. It is unreachable by the conjunction of two facts, neither sufficient alone. First, the
     * lexer token {@code CHARACTER_LITERAL} is {@code '\'' (~['\\\r\n] | '\\' .) '\''}, so the literal
     * text handed to analysis is either {@code 'x'} or {@code '\\x'} and never longer — a program that
     * writes {@code 'ab'} is rejected by the lexer with {@code SOLV-LEX-001} before semantic analysis
     * runs, which is the part a fixture cannot get past. Second, of the two shapes the token can produce,
     * the one-character form and the supported-escape form are both accepted by
     * {@code SolvikSemanticAnalyzer#checkCharacterLiteral}, and the one remaining shape — a backslash
     * followed by an unsupported escape — is reported by the branch immediately before it as an invalid
     * escape. The final statement of that method is therefore reached by no input, and stands as the
     * fallthrough the language needs if the token ever widens.
     */
    private static final Set<DiagnosticCode> ALLOW_LIST = new LinkedHashSet<>();

    static {
        ALLOW_LIST.add(DiagnosticCode.TYPE_INVALID_CHARACTER_LITERAL);
        // A local declaration always writes an initializer, and a local's name is not in scope inside
        // that initializer, so no source can read an uninitialized local. The check remains as the
        // analyzer's guard if the declarator ever becomes optional again.
        ALLOW_LIST.add(DiagnosticCode.TYPE_UNINITIALIZED_VARIABLE);
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
     * Guards the meta-test itself. This test reads every test source file to decide what is covered, and
     * that set includes this file — so a code merely named in this file's own prose about coverage would
     * be counted as tested, and the allow-list would be empty while nothing actually exercised the code.
     *
     * <p>A code is therefore allowed to appear in this file only when it is allow-listed, because an
     * allow-list entry states the reason no test can drive it and that reason is what a reader has to be
     * able to find. A code that is not allow-listed must be covered by a fixture or by some other test
     * source, which is what this assertion checks.
     *
     * <p>Without this guard, deleting the last real test for a code and mentioning its name in a comment
     * here would keep the build green, and the point of the meta-test — that every code is reachable by
     * something a developer can run — would be lost to the file that enforces it.
     */
    @Test
    public void thisFileDoesNotCoverAnyDiagnosticCodeItself() throws IOException {
        Set<DiagnosticCode> namedHere = namedInThisFile();
        for (DiagnosticCode code : namedHere) {
            assertThat(ALLOW_LIST.contains(code))
                    .as(code + " is named only in this file's prose, which cannot count as coverage; "
                                    + "add a real test or allow-list it with a reason")
                    .isTrue();
        }
    }

    /**
     * Whether an allow-list entry is still needed: a code that a fixture or another test source now
     * covers should be removed from {@link #ALLOW_LIST}, because an entry whose reason has gone stale
     * claims unreachability that no longer holds and stops anyone from noticing the code became testable.
     */
    @Test
    public void everyAllowListedCodeIsStillUncoveredElsewhere() throws IOException {
        Set<DiagnosticCode> covered = new LinkedHashSet<>();
        // Excluding this file, exactly as the coverage decision does. This file names both allow-listed
        // constants to explain them, so a scan that counted those mentions would report the codes as
        // covered elsewhere and demand their entries be dropped -- deleting the only written account of
        // why each entry exists. A code that genuinely became testable is covered by a fixture or another
        // suite, which is what this checks for.
        covered.addAll(referencedInTestSources());
        covered.addAll(fixtureCodes());
        for (DiagnosticCode code : ALLOW_LIST) {
            assertThat(covered.contains(code))
                    .as(code + " is allow-listed as unreachable but a fixture or test now covers it; drop the entry")
                    .isFalse();
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

    /**
     * Every {@code DiagnosticCode.NAME} constant referenced in the language test sources, excluding this
     * file. This file is the meta-test itself, so counting its prose would let a comment certify a code as
     * tested; both guards that consult coverage use this method, and
     * {@link #namedInThisFile()} is the only way this file's own text is examined.
     */
    private static Set<DiagnosticCode> referencedInTestSources() throws IOException {
        Set<DiagnosticCode> referenced = new LinkedHashSet<>();
        Path root = testSourcesRoot();
        if (root == null) {
            return referenced;
        }
        for (Path file : Files.walk(root).filter(Files::isRegularFile).toList()) {
            if (file.getFileName().toString().equals("DiagnosticCode.java") //
                    || file.getFileName().toString().equals("SolvikDiagnosticCodeCoverageTest.java")) {
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

    /**
     * Every {@code DiagnosticCode.NAME} constant named anywhere in this file's own source. The file is
     * located through {@link #testSourcesRoot()}, so the lookup works whether tests run from the module
     * root or the repository root.
     */
    private static Set<DiagnosticCode> namedInThisFile() throws IOException {
        Set<DiagnosticCode> named = new LinkedHashSet<>();
        Path root = testSourcesRoot();
        if (root == null) {
            return named;
        }
        Path self = null;
        for (Path file : Files.walk(root).filter(Files::isRegularFile).toList()) {
            if (file.getFileName().toString().equals("SolvikDiagnosticCodeCoverageTest.java")) {
                self = file;
                break;
            }
        }
        assertThat(self).as("this test file must be findable under " + root).isNotNull();
        String text = Files.readString(self, StandardCharsets.UTF_8);
        for (String name : diagnosticNames()) {
            if (Pattern.compile("\\b" + Pattern.quote(name) + "\\b").matcher(text).find()) {
                named.add(DiagnosticCode.valueOf(name));
            }
        }
        return named;
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
