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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.PhysicalLineTokenSource;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.parser.generated.SolvikLexer;
import org.solvik.source.SourceFile;

/**
 * Corpus-wide gate for the removed keyword vocabulary.
 *
 * <p>{@code val}, {@code open}, and {@code sealed} are reserved words that no longer begin or
 * introduce anything: the grammar declares no production that consumes them, and the parser reports
 * one diagnostic ({@code SOLV-PARS-006}) when a program uses one. The keyword overhaul migrated
 * every committed Solvik program to the replacement vocabulary, and this test is what keeps the
 * corpus migrated rather than merely intending to be.
 *
 * <p>The assertion is made through the lexer rather than through text search, and that choice is the
 * substance of the test. A word-boundary search cannot tell the removed keyword {@code val} from a
 * string that happens to print {@code "val"} or a comment that discusses the old spelling, so a text
 * gate either fails on SOL-TCK-0390 -- which prints the word precisely to show it is ordinary data
 * now -- or grows an exception list that quietly admits anything else of the same shape. The lexer
 * already knows the difference: SOL-TCK-0390's occurrence arrives as a {@code STRING} token, while a
 * reintroduced declaration would arrive as {@code VAL}. Reserved words are allowed only as data, so
 * the rule is "no {@code VAL}, {@code OPEN}, or {@code SEALED} token in any committed program", with
 * no exceptions to maintain.
 *
 * <p>Comments are checked as text on top of the token rule, because a comment is the one place a
 * keyword mention is invisible to the tokens and still misleading to a reader: the corpus must not
 * describe a program as using syntax the language no longer has. Prose that merely contains the word
 * ("leaves open") is ordinary English and is not a keyword mention, so the comment rule requires the
 * backticked or code-shaped spelling rather than the bare word.
 */
public final class SolvikRemovedKeywordCorpusTest {

    /** Token types that must never be delivered for a committed program. */
    private static final Set<String> REMOVED_TOKENS = Set.of("VAL", "OPEN", "SEALED");

    /**
     * A comment that presents a removed word as syntax: the backticked form, or the word in a
     * position where only syntax belongs. English uses of the same words are not keyword mentions.
     */
    private static final java.util.regex.Pattern REMOVED_AS_SYNTAX =
            java.util.regex.Pattern.compile("`(val|open|sealed)`|\\b(val|open|sealed)\\s+(var|class|func|static|override)\\b");

    /** Every {@code .sol} program in the repository, from any working directory. */
    private static List<Path> programs() throws IOException {
        Path root = repositoryRoot();
        List<Path> found = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".sol"))
                    .filter(SolvikRemovedKeywordCorpusTest::isCommittedSource)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(found::add);
        }
        return found;
    }

    /**
     * Restrict the sweep to sources that are part of the repository rather than build output.
     *
     * <p>Filtering by directory name rather than by "is under a {@code target/} tree" keeps the
     * predicate independent of the build layout: an accidental copy left anywhere else is still
     * checked, which is the direction that matters, because a gate that silently skips a directory
     * is not a gate.
     */
    private static boolean isCommittedSource(Path path) {
        for (Path part : path) {
            String name = part.toString();
            if (name.equals("target") || name.equals("build") || name.equals("node_modules")
                    || name.equals(".git")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Walk up from the working directory to the directory holding the language specification.
     *
     * <p>Maven runs tests from the module directory and an IDE usually from the repository root, so
     * a fixed relative path would silently sweep nothing in one of the two. Walking up to a marker
     * file makes the sweep independent of both, and fails loudly if the marker is absent rather than
     * passing over zero files.
     */
    private static Path cachedRoot;

    private static Path repositoryRoot() {
        if (cachedRoot == null) {
            cachedRoot = findRepositoryRoot();
        }
        return cachedRoot;
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("docs").resolve("LANGUAGE_SPEC.md"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("no ancestor of " + current + " holds docs/LANGUAGE_SPEC.md;"
                + " the corpus sweep needs the repository root to locate the corpus");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path relative(Path path) {
        return repositoryRoot().relativize(path);
    }

    /** The sweep must actually be sweeping. */
    @Test
    public void corpusIsLargeEnoughForTheSweepToBeMeaningful() throws IOException {
        List<Path> programs = programs();
        assertThat(programs.size()).as("committed .sol programs found from %s", Path.of("").toAbsolutePath())
                .isGreaterThan(500);
    }

    /**
     * No committed program delivers a removed keyword token.
     *
     * <p>Lexing is done through the same line-boundary stage the parser reads from, so a token
     * that only appears after boundary placement cannot hide between the two views. Every offender is
     * collected into one failure message rather than stopping at the first, because a migration that
     * regressed in several places should say so in one run.
     */
    @Test
    public void noCommittedProgramUsesARemovedKeywordToken() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path program : programs()) {
            for (String violation : removedTokenViolations(read(program))) {
                offenders.add(relative(program) + ": " + violation);
            }
        }
        assertThat(offenders).as("committed programs using `val`, `open`, or `sealed` as syntax").isEmpty();
    }

    /** Token names, with line/column, for every removed-keyword token in one program. */
    private static List<String> removedTokenViolations(String source) {
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        CommonTokenStream tokens = new CommonTokenStream(new PhysicalLineTokenSource(lexer));
        tokens.fill();
        List<String> violations = new ArrayList<>();
        for (Token token : tokens.getTokens()) {
            String name = SolvikLexer.VOCABULARY.getSymbolicName(token.getType());
            if (REMOVED_TOKENS.contains(name)) {
                violations.add(name + " at line " + token.getLine() + " column " + token.getCharPositionInLine()
                        + " (\"" + token.getText() + "\")");
            }
        }
        return violations;
    }

    /**
     * The production front end reports no removed-syntax diagnostic anywhere in the corpus.
     *
     * <p>The token gate above is lexical, and {@code SOLV-PARS-006} -- the diagnostic the parser
     * raises for a removed keyword -- is produced from that same lexer vocabulary, so a program that
     * passed the token gate should never trigger it. Asserting it anyway, through
     * {@link org.solvik.parser.SolvikParser#parse} rather than a hand-wired pipeline, closes the gap
     * between the two: it proves the shipped entry point, including its fast first stage, also finds
     * no removed vocabulary across the whole corpus. Parse errors of every other kind are expected --
     * many corpus programs are rejection fixtures -- so only this one diagnostic code is forbidden.
     */
    @Test
    public void noCommittedProgramDrawsTheRemovedSyntaxDiagnostic() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path program : programs()) {
            SolvikParseResult result = SolvikParser.parse(
                    new SourceFile(relative(program).toString(), read(program)));
            for (Diagnostic diagnostic : result.diagnostics().all()) {
                if (diagnostic.code() == DiagnosticCode.PARSER_UNSUPPORTED_REMOVED_SYNTAX) {
                    offenders.add(relative(program) + ": " + diagnostic.message());
                }
            }
        }
        assertThat(offenders).as("programs the front end rejects as removed syntax").isEmpty();
    }

    /**
     * No committed comment describes a program as using the removed spelling.
     *
     * <p>Comments carry no tokens, so the token gate cannot see them, and a stale comment is the
     * residue most likely to survive a rename: it misleads the next reader about what the language
     * accepts. Only a syntax-shaped mention is reported, so a sentence like "leaves open" in prose
     * about underspecified behavior is left alone.
     */
    @Test
    public void noCommittedCommentDescribesRemovedSyntax() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path program : programs()) {
            String source = read(program);
            int line = 0;
            for (String text : source.split("\n", -1)) {
                line++;
                int slash = text.indexOf("//");
                if (slash < 0) {
                    continue;
                }
                java.util.regex.Matcher matcher = REMOVED_AS_SYNTAX.matcher(text.substring(slash));
                if (matcher.find()) {
                    offenders.add(relative(program) + ":" + line + ": " + text.trim());
                }
            }
        }
        assertThat(offenders).as("comments presenting `val`, `open`, or `sealed` as Solvik syntax").isEmpty();
    }

    /**
     * Golden outputs must not print the removed spellings either.
     *
     * <p>A {@code .output} file is the expected behavior of a program, and a program can only print a
     * removed word as data. Pinning that keeps a golden from recording a string the corpus no longer
     * produces, which is how a stale expectation would otherwise survive unnoticed.
     */
    @Test
    public void noGoldenOutputDependsOnARemovedKeywordSpelling() throws IOException {
        Path root = repositoryRoot();
        List<Path> goldens = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".output"))
                    .filter(SolvikRemovedKeywordCorpusTest::isCommittedSource)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(goldens::add);
        }
        assertThat(goldens.size()).as("golden .output files found").isGreaterThan(50);
        List<String> offenders = new ArrayList<>();
        for (Path golden : goldens) {
            String text = read(golden);
            java.util.regex.Matcher matcher = REMOVED_AS_SYNTAX.matcher(text);
            if (matcher.find()) {
                offenders.add(root.relativize(golden) + ": " + matcher.group());
            }
        }
        assertThat(offenders).as("golden output presenting a removed word as syntax").isEmpty();
    }
}
