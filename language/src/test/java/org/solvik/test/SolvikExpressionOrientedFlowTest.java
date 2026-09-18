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

import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * Flow-analysis tests for value-required bodies whose statement sequences contain every control-flow
 * form. These exercise {@code completionOf}, {@code completionOfElse}, {@code loopCompletion}, and
 * the reachability break in {@code flowOfStatementSequence}, which a simple expression-only value
 * block does not reach (docs/LANGUAGE_SPEC.md section 21.1).
 */
public final class SolvikExpressionOrientedFlowTest {

    private static void check(String text) {
        CompilationUnitNode unit = parseOk("flow.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void valueBlockWithEveryStatementFormBeforeItsTail() {
        check("""
                func run(flag: Boolean, n: Int): Int {
                    val x = {
                        if (flag) {
                            print("a")
                        } else {
                            print("b")
                        }
                        if (flag) {
                            print("no else")
                        }
                        while (flag) {
                            break
                        }
                        for (var i: Int = 0; i < n; i = i + 1) {
                            continue
                        }
                        for (i in 0...n) {
                            break
                        }
                        {
                            print("nested")
                        }
                        switch (n) {
                            case 1:
                                print("one")
                            default:
                                print("other")
                        }
                        n + 1
                    }
                    return x
                }
                """);
    }

    @Test
    public void statementIfWithElseIfChainBeforeItsTail() {
        check("""
                func run(a: Boolean, b: Boolean): Int {
                    val x = {
                        if (a) {
                            print("a")
                        } else if (b) {
                            print("b")
                        } else {
                            print("c")
                        }
                        1
                    }
                    return x
                }
                """);
    }

    @Test
    public void statementSwitchWithoutDefaultBeforeItsTail() {
        check("""
                func run(n: Int): Int {
                    val x = {
                        switch (n) {
                            case 1:
                                print("one")
                        }
                        2
                    }
                    return x
                }
                """);
    }

    @Test
    public void aTailIfWithElseIfChainIsConvertedToAnExpression() {
        check("""
                func run(a: Boolean, b: Boolean): Int {
                    val x = {
                        if (a) {
                            1
                        } else if (b) {
                            2
                        } else {
                            3
                        }
                    }
                    return x
                }
                """);
    }

    @Test
    public void aTailSwitchIsConvertedToAnExpression() {
        check("""
                func run(n: Int): String {
                    val x = {
                        switch (n) {
                            case 1:
                                "one"
                            default:
                                "other"
                        }
                    }
                    return x
                }
                """);
    }

    @Test
    public void anUnreachableTailAfterAnAlwaysAbruptStatementIsIgnored() {
        check("""
                func run(flag: Boolean): Int {
                    val x = {
                        if (flag) {
                            return 1
                        } else {
                            return 2
                        }
                        print("unreachable")
                        3
                    }
                    return x
                }
                """);
    }
}
