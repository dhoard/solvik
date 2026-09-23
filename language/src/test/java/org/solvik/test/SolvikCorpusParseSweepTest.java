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
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.solvik.ast.AstNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticSeverity;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.source.SourceFile;

/**
 * Parses every checked-in Solvik program through the front-end parser directly and asserts the
 * parser contract on each: never an exception, spans that are valid intervals of their own source,
 * diagnostics only for rejected input, and a repeatable outcome.
 *
 * <p>The execution suites reach these files only through the whole pipeline, so a program rejected
 * for a semantic reason never exercises its parse result here. This sweep therefore covers the
 * lexer, semicolon insertion, and grammar of the entire corpus, including the programs that are
 * expected to be rejected later, and it fails on a span regression in a construct that no
 * execution test prints.
 */
public final class SolvikCorpusParseSweepTest {

    /** Every {@code .sol} file under the corpus directories. */
    private static List<Path> corpusPrograms() throws IOException {
        List<Path> programs = new ArrayList<>();
        for (Path directory : corpusDirectories()) {
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (Stream<Path> files = Files.list(directory)) {
                files.filter(path -> path.getFileName().toString().endsWith(".sol")).sorted().forEach(programs::add);
            }
        }
        return programs;
    }

    private static List<Path> corpusDirectories() {
        // Tests run from the module root or the repository root depending on the invocation.
        List<Path> directories = new ArrayList<>();
        for (String child : List.of("tests", "tests/examples", "tests/regression", "tests/diagnostics")) {
            directories.add(Path.of(child));
            directories.add(Path.of("language", child));
        }
        return directories;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One dynamic test per corpus program, so a failure names the offending file. */
    @TestFactory
    public Stream<DynamicTest> everyCorpusProgramParsesWithoutThrowing() throws IOException {
        List<Path> programs = corpusPrograms();
        assertThat(programs.size()).as("the corpus must not be empty").isGreaterThan(50);
        Stream.Builder<DynamicTest> tests = Stream.builder();
        for (Path program : programs) {
            tests.add(DynamicTest.dynamicTest("parse: " + program.getFileName(), () -> {
                String text = read(program);
                String name = program.getFileName().toString();
                SolvikParseResult result = SolvikParser.parse(new SourceFile(name, text));
                if (result.isSuccess()) {
                    assertThat(result.diagnostics().hasErrors()).as("accepted parse carries no diagnostics: " + name).isFalse();
                    assertSpans(result.requireAst(), text, name, 0);
                } else {
                    assertThat(result.ast().isPresent()).as("rejected parse exposes no AST: " + name).isFalse();
                    assertThat(result.diagnostics().hasErrors()).as("rejected parse carries diagnostics: " + name).isTrue();
                    for (Diagnostic d : result.diagnostics().all()) {
                        assertThat(d.severity()).as("diagnostic severity in " + name).isEqualTo(DiagnosticSeverity.ERROR);
                        assertThat(d.span().sourceId()).as("diagnostic belongs to the parsed source in " + name).isZero();
                        assertThat(d.span().startOffset()).as("diagnostic start in bounds in " + name).isBetween(0, text.length());
                        assertThat(d.span().endOffset()).as("diagnostic end in bounds in " + name).isBetween(d.span().startOffset(), text.length());
                    }
                }
            }));
        }
        return tests.build();
    }

    /**
     * Every node span must be an ordered interval inside the source, and every child span must sit
     * inside its parent's span. The second property is the one a mis-synthesized semicolon or a
     * mis-continued token breaks first.
     */
    private static void assertSpans(AstNode node, String text, String sourceName, int depth) {
        assertThat(depth).as("test recursion guard for " + sourceName).isLessThan(2000);
        assertThat(node.span().startOffset()).as("span start >= 0 for " + node.kind() + " in " + sourceName).isGreaterThanOrEqualTo(0);
        assertThat(node.span().endOffset()).as("span end <= length for " + node.kind() + " in " + sourceName).isLessThanOrEqualTo(text.length());
        assertThat(node.span().endOffset()).as("span ordered for " + node.kind() + " in " + sourceName).isGreaterThanOrEqualTo(node.span().startOffset());
        for (AstNode child : node.children()) {
            assertThat(child.span().startOffset()).as("child starts inside parent " + node.kind() + " in " + sourceName) //
                    .isGreaterThanOrEqualTo(node.span().startOffset());
            assertThat(child.span().endOffset()).as("child ends inside parent " + node.kind() + " in " + sourceName) //
                    .isLessThanOrEqualTo(node.span().endOffset());
            assertSpans(child, text, sourceName, depth + 1);
        }
    }

    /** The sweep must actually be exercising accepted programs, not only rejected ones. */
    @Test
    public void corpusContainsAcceptedProgramsSoTheSweepIsNotVacuous() throws IOException {
        int accepted = 0;
        for (Path program : corpusPrograms()) {
            if (SolvikParser.parse(new SourceFile(program.getFileName().toString(), read(program))).isSuccess()) {
                accepted++;
            }
        }
        assertThat(accepted).as("most corpus programs are valid Solvik and must parse").isGreaterThan(20);
    }

    /** Parsing the whole corpus twice must produce identical results, including across runs. */
    @Test
    public void corpusParsingIsRepeatable() throws IOException {
        for (Path program : corpusPrograms()) {
            String text = read(program);
            String name = program.getFileName().toString();
            assertThat(render(SolvikParser.parse(new SourceFile(name, text)))).as("second parse of " + name) //
                    .isEqualTo(render(SolvikParser.parse(new SourceFile(name, text))));
        }
    }

    private static String render(SolvikParseResult result) {
        StringBuilder sb = new StringBuilder();
        if (!result.isSuccess()) {
            for (Diagnostic d : result.diagnostics().all()) {
                sb.append(d.code().name()).append('[').append(d.span().startOffset()).append("..").append(d.span().endOffset()).append("] ");
            }
            return sb.toString();
        }
        renderNode(result.requireAst(), sb);
        return sb.toString();
    }

    private static void renderNode(AstNode node, StringBuilder sb) {
        sb.append(node.kind()).append('[').append(node.span().startOffset()).append("..").append(node.span().endOffset()).append("] ");
        for (AstNode child : node.children()) {
            renderNode(child, sb);
        }
    }
}
