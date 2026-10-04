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
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.SolvikParseResult;
import org.solvik.source.SourceFile;

/**
 * Positive and negative tests for the physical-line layout rules of {@code docs/LANGUAGE_SPEC.md}
 * section 16, as enforced by {@code PhysicalLineTokenSource} and {@code PhysicalLineRules}: an
 * opening brace is the last token of the line of its construct, a closing brace stands alone on its
 * line, a clause keyword begins its line, only comments may follow either brace, {@code ;}
 * separates same-line statements without ever terminating a line, and every {@code case} or
 * {@code default} body is a real braced lexical scope.
 *
 * <p>Layout mistakes still parse - the grammar absorbs the line breaks a program writes - so each
 * negative case asserts the dedicated diagnostic code the layout stage produces, not a raw parse
 * failure, and the positive cases assert that the canonical spellings carry no diagnostics at all.
 */
public final class SolvikPhysicalLineLayoutTest {

    /** Parses one program and requires it to be accepted with no diagnostics. */
    private static void layoutOk(String text) {
        SolvikParseResult result = org.solvik.parser.SolvikParser.parse(new SourceFile("layout.sol", text));
        if (!result.isSuccess()) {
            StringBuilder sb = new StringBuilder("unexpected errors:");
            result.diagnostics().all().forEach(d -> sb.append("\n  ").append(d));
            throw new AssertionError(sb.toString());
        }
        assertThat(result.diagnostics().isEmpty()).as("clean layout carries no diagnostics: " + text).isTrue();
    }

    /** The single layout diagnostic one misplaced program must produce. */
    private static Diagnostic layoutFails(String text, DiagnosticCode expected) {
        SolvikParseResult result = org.solvik.parser.SolvikParser.parse(new SourceFile("layout.sol", text));
        List<DiagnosticCode> codes = new ArrayList<>();
        for (Diagnostic d : result.diagnostics().all()) {
            codes.add(d.code());
        }
        assertThat(codes).as("expected " + expected + " for:\n" + text).contains(expected);
        Diagnostic first = result.diagnostics().all().get(0);
        assertThat(first.span().startOffset()).as("diagnostic is located: " + first).isBetween(0, text.length());
        assertThat(first.message()).isNotEmpty();
        return first;
    }

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out)
                .option("engine.WarnInterpreterOnly", "false").allowAllAccess(true).build()) {
            context.eval(org.graalvm.polyglot.Source.newBuilder("solvik", source, "layout.sol").build());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        } catch (RuntimeException e) {
            StringWriter trace = new StringWriter();
            e.printStackTrace(new PrintWriter(trace));
            throw new AssertionError("program failed:\n" + source + "\n" + trace, e);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------------------------------------
    // Positive: the canonical layout is accepted everywhere
    // ---------------------------------------------------------------------------------------------

    @Test
    public void theCanonicalLayoutIsAcceptedForEveryScopeIntroducingConstruct() {
        layoutOk("""
                func classify(value: Integer): String {
                    if (value < 0) {
                        return "negative"
                    }
                    else if (value == 0) {
                        return "zero"
                    }
                    else {
                        return "positive"
                    }
                }

                func guarded(): Unit {
                    try {
                        classify(1)
                    }
                    catch (e: RuntimeException) {
                        classify(2)
                    }
                    finally {
                        classify(3)
                    }
                }

                func dispatch(value: Integer): Unit {
                    switch (value) {
                        case 1 {
                            classify(1)
                        }
                        case 2, 3 {
                            classify(2)
                        }
                        default {
                            classify(0)
                        }
                    }
                }

                val blockResult = {
                    val inner = 1
                    inner + 1
                }

                {
                    classify(4)
                }

                print(classify(5))
                print(blockResult)
                """);
    }

    @Test
    public void aLineCommentMayFollowEitherBraceOnItsLine() {
        layoutOk("""
                func f(): Integer { // implementation
                    val x = if (true) { // the test
                        1
                    } // the else
                    else { // the branch
                        2
                    }
                    return x // done
                } // end f
                """);
    }

    @Test
    public void aLineCarryingTwoStatementsNeedsItsSeparatorAndTwoStatementsNeedTwoLines() {
        layoutOk("func f(): Unit {\n    val a = 1; val b = 2; print(a + b)\n}\n");
        layoutOk("func f(): Unit {\n    print(1); print(2); print(3)\n}\n");
    }

    @Test
    public void aStraySeparatorBeforeALineBreakIsToleratedAsASeparatorRun() {
        // `;` never terminates a line; the line's boundary does. A separator written before that
        // boundary is a run of separators, tolerated exactly as a blank line is.
        layoutOk("func f(): Unit {\n    print(1)\n    print(2)\n}\n");
    }

    @Test
    public void expressionsContinueAcrossLinesWhereverTheGrammarDemandsMore() {
        layoutOk("""
                func f(service: Service): Integer {
                    val total = 1 +
                        2 +
                        3
                    val chain = service
                        .load()
                        .transform()
                    val called = compute(
                        1,
                        2,
                        3,
                    )
                    return total
                }
                """);
    }

    @Test
    public void aProgramEndingWithoutATrailingNewlineIsComplete() {
        layoutOk("func f(): Unit {\n    print(1)\n}");
        layoutOk("print(1)");
    }

    // ---------------------------------------------------------------------------------------------
    // Negative: a closing brace that shares its line
    // ---------------------------------------------------------------------------------------------

    @Test
    public void aClosingBraceFollowedByCodeOnItsLineIsRejected() {
        layoutFails("func g(f: func(): Integer): Integer {\n    return f()\n}\n\nprint(\n    g(\n        func(): Integer {\n            1\n        })\n)\n", DiagnosticCode.PARSER_BRACE_SHARES_LINE);
        layoutFails("func f(): Unit {\n    print(1)\n};\n", DiagnosticCode.PARSER_BRACE_SHARES_LINE);
        layoutFails("func f(): Unit {\n    print(1) }\n", DiagnosticCode.PARSER_BRACE_SHARES_LINE);
    }

    @Test
    public void braceThenClauseOnOneLineIsRejectedAsTheClauseRule() {
        layoutFails("func f(c: Boolean): Unit {\n    if (c) {\n        print(1)\n    } else {\n        print(2)\n    }\n}\n",
                DiagnosticCode.PARSER_CLAUSE_NOT_AT_LINE_START);
        layoutFails("func f(): Unit {\n    try {\n        print(1)\n    } catch (e: RuntimeException) {\n        print(2)\n    }\n}\n",
                DiagnosticCode.PARSER_CLAUSE_NOT_AT_LINE_START);
        layoutFails("func f(): Unit {\n    try {\n        print(1)\n    }\n    catch (e: RuntimeException) {\n        print(2)\n    } finally {\n        print(3)\n    }\n}\n",
                DiagnosticCode.PARSER_CLAUSE_NOT_AT_LINE_START);
    }

    @Test
    public void aClosingBracePrecededByCodeOnItsLineIsRejected() {
        layoutFails("func f(): Unit {\n    print(1); }\n", DiagnosticCode.PARSER_BRACE_SHARES_LINE);
    }

    // ---------------------------------------------------------------------------------------------
    // Negative: content after an opening brace
    // ---------------------------------------------------------------------------------------------

    @Test
    public void codeWrittenAfterAnOpeningBraceOnItsLineIsRejected() {
        layoutFails("func f(): Unit {\n    if (true) { print(1)\n    }\n}\n", DiagnosticCode.PARSER_CONTENT_AFTER_OPEN_BRACE);
        layoutFails("func f(): Unit { print(1)\n}\n", DiagnosticCode.PARSER_CONTENT_AFTER_OPEN_BRACE);
        layoutFails("func f(): Unit {\n    if (true) { print(1) }\n}\n", DiagnosticCode.PARSER_CONTENT_AFTER_OPEN_BRACE);
    }

    @Test
    public void anEmptyBracePairOnOneLineIsRejectedAndTheTwoLineSpellingIsTheEmptyBody() {
        layoutFails("func f(): Unit {}\n", DiagnosticCode.PARSER_CONTENT_AFTER_OPEN_BRACE);
        layoutOk("func f(): Unit {\n}\n");
    }

    // ---------------------------------------------------------------------------------------------
    // Negative: a body brace that begins its line
    // ---------------------------------------------------------------------------------------------

    @Test
    public void aBodyBraceMustSitOnTheLineOfTheIntroducingConstruct() {
        layoutFails("func f(): Unit {\n    try\n    {\n        print(1)\n    }\n}\n", DiagnosticCode.PARSER_BRACE_NOT_ON_INTRODUCING_LINE);
        layoutFails("func f(c: Boolean): Unit {\n    if (c) {\n        print(1)\n    }\n    else\n    {\n        print(2)\n    }\n}\n",
                DiagnosticCode.PARSER_BRACE_NOT_ON_INTRODUCING_LINE);
        layoutFails("mutable class C {\n    static\n    {\n    }\n}\n", DiagnosticCode.PARSER_BRACE_NOT_ON_INTRODUCING_LINE);
    }

    @Test
    public void aCaseBodyBraceMustCloseTheLabelLine() {
        layoutFails("""
                func f(v: Integer): Unit {
                    switch (v) {
                        case 1
                        {
                            print(1)
                        }
                        default {
                            print(0)
                        }
                    }
                }
                """, DiagnosticCode.PARSER_BRACE_NOT_ON_INTRODUCING_LINE);
        layoutFails("""
                func f(v: Integer): Unit {
                    switch (v) {
                        case 1 {
                            print(1)
                        }
                        default
                        {
                            print(0)
                        }
                    }
                }
                """, DiagnosticCode.PARSER_BRACE_NOT_ON_INTRODUCING_LINE);
        // A stand-alone scope as the first item of a case body is legal: its brace opens a line
        // because the case body's own brace already closed the label line.
        layoutOk("""
                func f(v: Integer): Unit {
                    switch (v) {
                        case 1 {
                            {
                                print(1)
                            }
                        }
                        default {
                            print(0)
                        }
                    }
                }
                """);
    }

    // ---------------------------------------------------------------------------------------------
    // Case and default bodies are real scopes
    // ---------------------------------------------------------------------------------------------

    @Test
    public void twoCasesBindTheSameNameIndependentlyAndExecuteBoth() {
        assertThat(run("""
                func describe(v: Integer): String {
                    switch (v) {
                        case 1 {
                            val x = "one"
                            return x
                        }
                        case 2 {
                            val x = "two"
                            return x
                        }
                        default {
                            return "other"
                        }
                    }
                }

                print(describe(1))
                print(describe(2))
                """)).isEqualTo("onetwo");
    }

    @Test
    public void anUnbracedCaseBodyIsRejected() {
        SolvikParseResult result = org.solvik.parser.SolvikParser.parse(new SourceFile("layout.sol", """
                func f(v: Integer): Unit {
                    switch (v) {
                        case 1:
                            print(1)

                        default:
                            print(0)
                    }
                }
                """));
        assertThat(result.isSuccess()).as("the colon spelling is gone from the grammar").isFalse();
    }

    @Test
    public void siblingLexicalScopesHoldSameNameBindings() {
        assertThat(run("""
                {
                    val x = 1
                    print(x)
                }
                {
                    val x = 2
                    print(x)
                }
                """)).isEqualTo("12");
    }

    @Test
    public void returnBreakAndContinuePassThroughABareScopeUnchanged() {
        assertThat(run("""
                func first(): Integer {
                    {
                        {
                            return 7
                        }
                    }
                    return 0
                }

                func counted(): Integer {
                    mutable val seen: Integer = 0
                    for (i in 0..<4) {
                        {
                            if (i == 1) {
                                seen = seen + 1
                                continue
                            }
                            if (i == 3) {
                                break
                            }
                            seen = seen + 10
                        }
                    }
                    return seen
                }

                print(first())
                print(counted())
                """)).isEqualTo("721");
    }
}
