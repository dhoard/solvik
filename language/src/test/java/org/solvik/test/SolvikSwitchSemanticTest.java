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
import org.solvik.ast.AstKind;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.statement.ConstantCaseLabelNode;
import org.solvik.ast.statement.RegexCaseLabelNode;
import org.solvik.ast.statement.SwitchStmtNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.IntegerType;
import org.solvik.type.LongType;
import org.solvik.type.StringType;
import org.solvik.type.Type;

/**
 * Positive Phase 15 semantic tests (docs/LANGUAGE_SPEC.md section 13): constant label typing,
 * grouped labels, {@code default}, one-time regex case compilation, and the switch-specific
 * {@code break}/{@code continue} rules.
 */
public final class SolvikSwitchSemanticTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("switch.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
        return result.requireProgram();
    }

    private static SwitchStmtNode switchInFunction(CheckedProgram program, String name) {
        for (var declaration : program.unit().declarations()) {
            if (declaration instanceof FunctionDeclNode function && function.name().equals(name)) {
                return (SwitchStmtNode) function.body().statements().get(0);
            }
        }
        throw new AssertionError("no function named " + name);
    }

    @Test
    public void constantLabelsAreTypedAndRecorded() {
        CheckedProgram program = check("""
                func run(value: Integer): Unit {
                    switch (value) {
                        case 1:
                            print("one")
                        default:
                            print("other")
                    }
                }
                """);
        ConstantCaseLabelNode label = (ConstantCaseLabelNode) switchInFunction(program, "run").cases().get(0).labels().get(0);
        Type type = program.typeOf(label.expression()).orElseThrow();
        assertThat(type).isSameAs(IntegerType.INSTANCE);
    }

    @Test
    public void groupedLabelsAndNegativeConstantsAreAccepted() {
        CheckedProgram program = check("""
                func run(value: Integer): Unit {
                    switch (value) {
                        case -1, 0, 1:
                            print("unit")
                        default:
                            print("other")
                    }
                }
                """);
        SwitchStmtNode statement = switchInFunction(program, "run");
        assertThat(statement.cases().get(0).labels().size()).isEqualTo(3);
        assertThat(program.typeOf(((ConstantCaseLabelNode) statement.cases().get(0).labels().get(0)).expression()).orElseThrow()).isSameAs(IntegerType.INSTANCE);
    }

    @Test
    public void longConstantsRequireALongScrutinee() {
        CheckedProgram program = check("""
                func run(value: Long): Unit {
                    switch (value) {
                        case 1L:
                            print("one")
                        default:
                            print("other")
                    }
                }
                """);
        ConstantCaseLabelNode label = (ConstantCaseLabelNode) switchInFunction(program, "run").cases().get(0).labels().get(0);
        assertThat(program.typeOf(label.expression()).orElseThrow()).isSameAs(LongType.INSTANCE);
    }

    @Test
    public void aNullLabelMatchesAGNullableScrutinee() {
        CheckedProgram program = check("""
                func run(value: String?): Unit {
                    switch (value) {
                        case null:
                            print("none")
                        case "x":
                            print("x")
                        default:
                            print("other")
                    }
                }
                """);
        SwitchStmtNode statement = switchInFunction(program, "run");
        assertThat(((ConstantCaseLabelNode) statement.cases().get(0).labels().get(0)).expression().kind()).isSameAs(AstKind.NULL_LITERAL);
        assertThat(((ConstantCaseLabelNode) statement.cases().get(1).labels().get(0)).expression() instanceof org.solvik.ast.expression.StringLiteralNode).isTrue();
    }

    @Test
    public void aRegexCaseCompilesItsConstantPatternOnce() {
        CheckedProgram program = check("""
                func run(input: String): Unit {
                    switch (input) {
                        case regex r#"^\\d+$"#:
                            print("number")
                        default:
                            print("other")
                    }
                }
                """);
        RegexCaseLabelNode label = (RegexCaseLabelNode) switchInFunction(program, "run").cases().get(0).labels().get(0);
        assertThat(program.regexCasePatternOf(label).orElseThrow().source()).isEqualTo("^\\d+$");
    }

    @Test
    public void aRegexCaseWorksOnAConstantStringPatternToo() {
        CheckedProgram program = check("""
                func run(input: String): Unit {
                    switch (input) {
                        case regex "[a-z]+":
                            print("word")
                        default:
                            print("other")
                    }
                }
                """);
        RegexCaseLabelNode label = (RegexCaseLabelNode) switchInFunction(program, "run").cases().get(0).labels().get(0);
        assertThat(program.regexCasePatternOf(label).orElseThrow().source()).isEqualTo("[a-z]+");
    }

    @Test
    public void anEmptyDefaultIsAccepted() {
        CheckedProgram program = check("""
                func run(value: Integer): Unit {
                    switch (value) {
                        case 1:
                            print("one")
                        default:
                    }
                }
                """);
        assertThat(switchInFunction(program, "run").cases().size()).isEqualTo(2);
    }

    @Test
    public void aBreakInsideALoopNestedInACaseIsAccepted() {
        check("""
                func run(value: Integer): Unit {
                    switch (value) {
                        case 1:
                            while (true) {
                                break
                            }
                        default:
                            print("other")
                    }
                }
                """);
    }

    @Test
    public void aContinueInACaseTargetsAnEnclosingLoop() {
        check("""
                func run(value: Integer): Unit {
                    for (var i = 0; i < 3; i = i + 1) {
                        switch (value) {
                            case 1:
                                continue
                            default:
                                print("other")
                        }
                    }
                }
                """);
    }

    @Test
    public void aSwitchOverAConstantStringValueIsTyped() {
        CheckedProgram program = check("""
                func run(value: String): Unit {
                    switch (value) {
                        case "a":
                            print("a")
                        case "b":
                            print("b")
                        default:
                            print("other")
                    }
                }
                """);
        assertThat(program.typeOf(switchInFunction(program, "run").scrutinee()).orElseThrow()).isSameAs(StringType.INSTANCE);
    }
}
