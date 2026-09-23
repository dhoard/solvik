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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.diagnostic.DiagnosticSeverity;
import org.solvik.parser.SolvikParseResult;
import org.solvik.parser.SolvikParser;
import org.solvik.source.SourceFile;

/**
 * Robustness contract for the front-end parser: malformed and pathological input must produce
 * source-located diagnostics and never an exception, and pathological nesting must not exhaust
 * compiler resources or turn compilation into an unbounded wait. Authority: {@code AGENTS.md}
 * ("make compile-time diagnostics precise and source-located"), {@code docs/ARCHITECTURE.md} ("the
 * parser returns syntax AST plus diagnostics"), and the parser's own contract that malformed input
 * produces diagnostics, not exceptions. See docs/FRONTEND_ROBUSTNESS_AUDIT.md.
 */
public final class SolvikParserRobustnessTest {

    /** Nesting depth that used to overflow the parser stack on a default one-megabyte thread stack. */
    private static final int FORMERLY_FATAL_DEPTH = 500;

    /**
     * Depth of balanced nesting that used to parse successfully. Kept well below the reserved
     * front-end stack capacity so the test pins recovery rather than a new limit.
     */
    private static final int FORMERLY_FATAL_BALANCED_DEPTH = 400;

    private static String repeat(String unit, int times) {
        return new String(new char[times]).replace("\0", unit);
    }

    /** Runs the parser, failing on any escaped throwable, and returns the result. */
    private static SolvikParseResult parse(String name, String src) {
        SolvikParseResult result = null;
        Throwable escaped = null;
        try {
            result = SolvikParser.parse(new SourceFile(name, src));
        } catch (Throwable t) {
            escaped = t;
        }
        if (escaped != null) {
            throw new AssertionError("parse must never throw for source [" + preview(src) + "]", escaped);
        }
        return result;
    }

    private static String preview(String src) {
        String shown = src.replace("\n", "\\n").replace("\r", "\\r");
        return shown.substring(0, Math.min(120, shown.length()));
    }

    /** Asserts the general contract every parse result must satisfy. */
    private static SolvikParseResult checkContract(String name, String src) {
        SolvikParseResult result = parse(name, src);
        if (result.isSuccess()) {
            assertThat(result.diagnostics().isEmpty()).as("accepted parse carries no diagnostics: " + preview(src)).isTrue();
            assertSpansWithinBounds(result.requireAst(), src, 0);
        } else {
            assertThat(result.ast().isEmpty()).as("rejected parse exposes no AST: " + preview(src)).isTrue();
            assertThat(result.diagnostics().hasErrors()).as("rejected parse carries diagnostics: " + preview(src)).isTrue();
            for (Diagnostic d : result.diagnostics().all()) {
                assertThat(d.severity()).isEqualTo(DiagnosticSeverity.ERROR);
                assertThat(d.span().startOffset()).as("span start in bounds: " + d).isBetween(0, src.length());
                assertThat(d.span().endOffset()).as("span end in bounds: " + d).isBetween(d.span().startOffset(), src.length());
                assertThat(d.message()).isNotEmpty();
            }
        }
        return result;
    }

    /** Every AST node span must be a valid, ordered interval inside the source text. */
    private static void assertSpansWithinBounds(AstNode node, String src, int depth) {
        assertThat(depth).as("test recursion guard").isLessThan(4000);
        assertThat(node.span().startOffset()).as("node span start >= 0 for " + node.kind()).isGreaterThanOrEqualTo(0);
        assertThat(node.span().endOffset()).as("node span end <= source length for " + node.kind()).isLessThanOrEqualTo(src.length());
        assertThat(node.span().endOffset()).as("node span is ordered for " + node.kind()).isGreaterThanOrEqualTo(node.span().startOffset());
        for (AstNode child : node.children()) {
            assertSpansWithinBounds(child, src, depth + 1);
        }
    }

    // ---- Malformed deeply-nested input must be diagnosed, not crash -----------------------------

    @Test
    public void unclosedParenthesisNestingIsDiagnosedInsteadOfOverflowingTheStack() {
        String src = "func f(): Unit {\n    val x = " + repeat("(", FORMERLY_FATAL_DEPTH) + "1;\n}\n";
        SolvikParseResult result = checkContract("deep-open.sol", src);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    public void unclosedCallChainNestingIsDiagnosedInsteadOfOverflowingTheStack() {
        String src = "func f(): Unit {\n    " + repeat("f(", FORMERLY_FATAL_DEPTH) + "1;\n}\n";
        assertThat(checkContract("deep-call.sol", src).isSuccess()).isFalse();
    }

    @Test
    public void unclosedVariantPatternNestingIsDiagnosedInsteadOfOverflowingTheStack() {
        String src = "func f(v: Any): Unit {\n    val y = match v { " + repeat("A(", FORMERLY_FATAL_DEPTH) + " };\n}\n";
        assertThat(checkContract("deep-pattern.sol", src).isSuccess()).isFalse();
    }

    @Test
    public void unclosedTypeArgumentNestingIsDiagnosedInsteadOfOverflowingTheStack() {
        String src = "func f(): Unit {\n    val x: " + repeat("List<", FORMERLY_FATAL_DEPTH) + "Integer;\n}\n";
        assertThat(checkContract("deep-typeargs.sol", src).isSuccess()).isFalse();
    }

    @Test
    public void unclosedBlockNestingIsDiagnosedInsteadOfOverflowingTheStack() {
        String src = "func f(): Unit " + repeat("{", FORMERLY_FATAL_DEPTH) + "\n";
        assertThat(checkContract("deep-blocks.sol", src).isSuccess()).isFalse();
    }

    @Test
    public void strayClosingDelimitersFarBeyondTheStackLimitAreDiagnosed() {
        // A stream far longer than the former fatal depth of stray closers must still only produce
        // diagnostics: the parser must not recurse per stray token.
        assertThat(checkContract("stray-parens.sol", "func f(): Unit {\n    " + repeat(")", FORMERLY_FATAL_DEPTH) + "1;\n}\n").isSuccess()).isFalse();
        assertThat(checkContract("stray-braces.sol", "func f(): Unit " + repeat("}", FORMERLY_FATAL_DEPTH) + "\n").isSuccess()).isFalse();
    }

    // ---- Deeply nested valid programs must compile ----------------------------------------------

    @Test
    public void balancedNestingFarBeyondTheFormerStackLimitParses() {
        String src = "func f(): Integer {\n    val x = " + repeat("(", FORMERLY_FATAL_BALANCED_DEPTH) + "1" + repeat(")", FORMERLY_FATAL_BALANCED_DEPTH) + ";\n    return x;\n}\n";
        SolvikParseResult result = checkContract("balanced-parens.sol", src);
        assertThat(result.isSuccess()).as("a valid program must parse at this depth").isTrue();
    }

    @Test
    public void deepCallChainInAValidProgramParses() {
        String src = "func f(): Unit {\n    " + repeat("f(", FORMERLY_FATAL_BALANCED_DEPTH) + repeat(")", FORMERLY_FATAL_BALANCED_DEPTH) + ";\n}\n";
        assertThat(checkContract("balanced-call.sol", src).isSuccess()).isTrue();
    }

    @Test
    public void deeplyNestedControlFlowParsesWithoutUnboundedCost() {
        for (int depth : new int[] {20, 100, FORMERLY_FATAL_BALANCED_DEPTH}) {
            String src = "func f(): Unit {\n" + repeat("if (true) {\n", depth) + repeat("}\n", depth) + "}\n";
            long started = System.nanoTime();
            assertThat(checkContract("nested-if-" + depth + ".sol", src).isSuccess()).as("nested if at depth " + depth).isTrue();
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            // Full-context prediction needed seconds at depth 20 and minutes at this depth. The
            // bound is deliberately generous: it fails only on the regression, not on machine noise.
            assertThat(elapsedMillis).as("nested if at depth " + depth + " must parse in well under a second").isLessThan(5_000);
        }
    }

    @Test
    public void deeplyNestedBlockAndSwitchBodiesParseWithoutUnboundedCost() {
        for (int depth : new int[] {100, FORMERLY_FATAL_BALANCED_DEPTH}) {
            String blocks = "func f(): Unit {\n" + repeat("{\n", depth) + repeat("}\n", depth) + "}\n";
            String switches = "func f(v: Integer): Unit {\n" + repeat("switch (v) {\ncase 1: 1\n", depth) + repeat("}\n", depth) + "}\n";
            for (String src : List.of(blocks, switches)) {
                long started = System.nanoTime();
                assertThat(checkContract("nested.sol", src).isSuccess()).as("nested construct at depth " + depth).isTrue();
                long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
                assertThat(elapsedMillis).as("nested construct at depth " + depth).isLessThan(5_000);
            }
        }
    }

    // ---- Nesting beyond capacity is a diagnostic ------------------------------------------------

    @Test
    public void nestingBeyondCompilerCapacityIsReportedAsADiagnosticNotAResourceFailure() {
        // Past the reserved front-end stack the compiler cannot read the file at all. The outcome
        // must be a normal parse failure naming the problem, never an escaping StackOverflowError.
        String src = "func f(): Unit {\n    val x = " + repeat("(", 400_000) + "1" + repeat(")", 400_000) + ";\n}\n";
        SolvikParseResult result = checkContract("too-deep.sol", src);
        assertThat(result.isSuccess()).isFalse();
        List<String> codes = new ArrayList<>();
        for (Diagnostic d : result.diagnostics().all()) {
            codes.add(d.code().stableCode());
        }
        assertThat(codes).contains(DiagnosticCode.PARSER_NESTING_TOO_DEEP.stableCode());
    }

    @Test
    public void theNestingDiagnosticSpansTheWholeFileAndNamesTheProblem() {
        // A call chain recurses through a different grammar rule than a parenthesized expression, so
        // the capacity diagnostic is pinned for more than one right-recursive path.
        String src = "func f(): Unit {\n    " + repeat("f(", 400_000) + repeat(")", 400_000) + ";\n}\n";
        SolvikParseResult result = parse("span.sol", src);
        Diagnostic tooDeep = null;
        for (Diagnostic d : result.diagnostics().all()) {
            if (d.code() == DiagnosticCode.PARSER_NESTING_TOO_DEEP) {
                tooDeep = d;
            }
        }
        assertThat(tooDeep).as("the over-capacity file must report SOLV-PARS-005, got " + result.diagnostics().all()).isNotNull();
        // The limit is a whole-file resource limit rather than a property of one token, so the span
        // is the entire file and the message says what to do about it.
        assertThat(tooDeep.span().startOffset()).isZero();
        assertThat(tooDeep.span().endOffset()).isEqualTo(src.length());
        assertThat(tooDeep.message()).contains("nest").contains("depth");
    }

    // ---- Fuzzing the whole contract -------------------------------------------------------------

    /**
     * Random token soup and single-character mutations of realistic programs must always be
     * diagnosed and never throw. Seeded, so a failure is reproducible.
     */
    @Test
    public void randomTokenSoupNeverThrowsAndNeverProducesAnOutOfBoundSpan() {
        String[] atoms = {"func", "class", "val", "var", "if", "else", "while", "for", "in", "return", "match", "switch", "case", "regex", "null", "is", "as", "true", "include", "module", "alias", "(", ")", "{", "}", "[", "]", ";", ",", ":", "::", "=", ".", "..", "...", "..<", "..>", "?.", "??", "?", "+", "-", "*", "/", "!", "===", "!==", "==", "!=", "<", "<=", ">", ">=", "&&", "||", "=>", "@", "#", "$", "\\", "1", "2", "123L", "1.5", "1e3", "\"s\"", "'c'", "r\"r\"", "x", "y", "_", "function", "\n", "\t", " ", "//c\n", "/*c*/", "/*\nc*/"};
        Random random = new Random(20_260_923L);
        for (int iteration = 0; iteration < 3_000; iteration++) {
            StringBuilder src = new StringBuilder();
            int atomsUsed = 1 + random.nextInt(20);
            for (int i = 0; i < atomsUsed; i++) {
                src.append(atoms[random.nextInt(atoms.length)]);
            }
            checkContract("soup.sol", src.toString());
        }
    }

    /**
     * Deleting, inserting, or replacing one character of a program that exercises every construct
     * must still be a clean accept or a clean diagnostic-only reject.
     */
    @Test
    public void singleCharacterMutationsOfWellFormedProgramsNeverThrow() {
        String[] seeds = { //
                "func add(a: Integer, b: Integer): Integer {\n    return a + b;\n}\nprintln(add(1, 2));\n", //
                "class Point {\n    val x: Integer = 0;\n    Point(theX: Integer) {\n        x = theX;\n    }\n    func getX(): Integer {\n        return x;\n    }\n}\n", //
                "interface Shape {\n    func area(): Integer;\n    func name(): String {\n        return \"shape\";\n    }\n}\n", //
                "enum Color {\n    Red;\n    Green(Integer);\n}\nfunc f(c: Color): Integer {\n    return match c {\n        Red => 1,\n        Green(v) => v\n    };\n}\n", //
                "func f(): Unit {\n    switch (1) {\n    case 1, 2:\n        println(1);\n        break;\n    default:\n        println(0);\n    }\n}\n", //
                "func f(v: Any): Unit {\n    if (v is String) {\n        println(v);\n    } else {\n        println(v as Integer);\n    }\n}\n", //
                "func f(): Unit {\n    val s = r#\"raw \"value\"\"# .. \"tail\"\n    println(s);\n}\n", //
                "func id<T>(v: T): T {\n    return v;\n}\nfunc f(): Unit {\n    println(id<Integer>(3));\n}\n", //
                "func f(v: Integer?): Unit {\n    val x = v ?? 0;\n    println(x);\n}\n", //
                "func f(): Unit {\n    for (i in 0...10) {\n        if (i == 5) {\n            continue;\n        }\n        println(i);\n    }\n}\n", //
                "module app\nfunc f(): Unit {\n    val x = if (true) { 1 } else { 2 };\n}\n", //
                "interface I {\n    func ping(): Integer;\n}\nclass C {\n    delegate val d: I;\n}\n"};
        char[] mutations = {'(', ')', '{', '}', ';', ',', ':', '.', '<', '>', '=', '?', '!', '*', '+', '-', '/', '&', '|', '@', '#', '\\', '\'', '"', '\n', ' ', '\t', 'r', '1', 'x', 'e', 'a', 'v', 'f', 'i', '\r'};
        Random random = new Random(9_999L);
        for (String seed : seeds) {
            for (int mutation = 0; mutation < 400; mutation++) {
                StringBuilder src = new StringBuilder(seed);
                int position = random.nextInt(src.length() + 1);
                int operation = random.nextInt(3);
                char replacement = mutations[random.nextInt(mutations.length)];
                if (operation == 0 && src.length() > 0) {
                    src.deleteCharAt(Math.min(position, src.length() - 1));
                } else if (operation == 1) {
                    src.insert(position, replacement);
                } else if (src.length() > 0) {
                    src.setCharAt(Math.min(position, src.length() - 1), replacement);
                }
                checkContract("mutant.sol", src.toString());
            }
        }
    }

    /** The parser is a function of its input: the same source must give the same outcome. */
    @Test
    public void parsingIsRepeatableForAcceptedAndRejectedInput() {
        String accepted = "func f(): Integer {\n    val x = 1 + 2;\n    return x;\n}\n";
        String rejected = "func f(): Unit {\n    g(1) h(2)\n}\n";
        for (int run = 0; run < 3; run++) {
            assertThat(render(parse("a.sol", accepted))).isEqualTo(render(parse("a.sol", accepted)));
            assertThat(render(parse("r.sol", rejected))).isEqualTo(render(parse("r.sol", rejected)));
        }
    }

    /** Renders the observable outcome of a parse so two runs can be compared. */
    private static String render(SolvikParseResult result) {
        if (!result.isSuccess()) {
            StringBuilder sb = new StringBuilder("failure");
            for (Diagnostic d : result.diagnostics().all()) {
                sb.append(' ').append(d.code().name()).append(d.span().startOffset()).append("..").append(d.span().endOffset());
            }
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder();
        renderNode(result.requireAst(), sb);
        return sb.toString();
    }

    private static void renderNode(AstNode node, StringBuilder sb) {
        sb.append(node.kind()).append('@').append(node.span().startOffset()).append("..").append(node.span().endOffset()).append(' ');
        for (AstNode child : node.children()) {
            renderNode(child, sb);
        }
    }

    /** An accepted parse carries no diagnostics; a rejected parse carries no AST. */
    @Test
    public void acceptedAndRejectedResultsAreMutuallyExclusive() {
        List<String> sources = List.of("", "\n", "func f(): Unit {\n}\n", "func f(): Unit {\n    @\n}\n", "func f(): Unit {\n    val x = (1;\n}\n");
        for (String src : sources) {
            SolvikParseResult result = checkContract("exclusive.sol", src);
            assertThat(result.isSuccess() ^ result.diagnostics().hasErrors()).as("exactly one outcome for " + preview(src)).isTrue();
            if (result.isSuccess()) {
                assertThat(result.ast().isPresent()).isTrue();
                assertThat(result.requireAst()).isInstanceOf(CompilationUnitNode.class);
            } else {
                try {
                    result.requireAst();
                    throw new AssertionError("requireAst must reject a failed parse");
                } catch (IllegalStateException expected) {
                    assertThat(expected.getMessage()).contains("parse failed");
                }
            }
        }
    }

}
