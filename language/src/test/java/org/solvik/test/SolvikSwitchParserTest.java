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
import static org.solvik.test.SolvikTestSupport.parseFails;
import static org.solvik.test.SolvikTestSupport.parseOk;

import org.junit.jupiter.api.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.IntLiteralNode;
import org.solvik.ast.expression.NameRefExprNode;
import org.solvik.ast.expression.RawStringLiteralNode;
import org.solvik.ast.expression.StringLiteralNode;
import org.solvik.ast.statement.CaseLabelNode;
import org.solvik.ast.statement.ConstantCaseLabelNode;
import org.solvik.ast.statement.ExprStmtNode;
import org.solvik.ast.statement.RegexCaseLabelNode;
import org.solvik.ast.statement.SwitchCaseNode;
import org.solvik.ast.statement.SwitchStmtNode;

/**
 * Phase 15 parser tests (docs/LANGUAGE_SPEC.md section 13): the non-fallthrough {@code switch}
 * statement, grouped constant case labels, {@code default}, implicit case body blocks, and the
 * {@code case regex <pattern>} label.
 */
public final class SolvikSwitchParserTest {

    private static SwitchStmtNode switchInFunction(CompilationUnitNode unit, String functionName) {
        for (var declaration : unit.declarations()) {
            if (declaration instanceof FunctionDeclNode function && function.name().equals(functionName)) {
                return (SwitchStmtNode) function.body().statements().get(0);
            }
        }
        throw new AssertionError("no function named " + functionName);
    }

    @Test
    public void switchRecordsScrutineeAndCasesInSourceOrder() {
        CompilationUnitNode unit = parseOk("s.sol", """
                func run(value: Int): Unit {
                    switch (value) {
                        case 1:
                            print("one")
                        case 2:
                            print("two")
                        default:
                            print("other")
                    }
                }
                """);
        SwitchStmtNode statement = switchInFunction(unit, "run");
        assertThat(statement.kind()).isEqualTo(AstKind.SWITCH_STMT);
        assertThat(statement.scrutinee().kind()).isEqualTo(AstKind.NAME_REF_EXPR);
        assertThat(((NameRefExprNode) statement.scrutinee()).name()).isEqualTo("value");
        assertThat(statement.cases().size()).isEqualTo(3);
        assertThat(statement.cases().get(0).isDefault()).isFalse();
        assertThat(statement.cases().get(1).isDefault()).isFalse();
        assertThat(statement.cases().get(2).isDefault()).isTrue();
        assertThat(statement.cases().get(2).labels().isEmpty()).isTrue();
    }

    @Test
    public void groupedConstantLabelsShareOneCase() {
        CompilationUnitNode unit = parseOk("s.sol", """
                func run(value: Int): Unit {
                    switch (value) {
                        case 1, 2, 3:
                            print("small")
                        default:
                            print("other")
                    }
                }
                """);
        SwitchCaseNode first = switchInFunction(unit, "run").cases().get(0);
        assertThat(first.labels().size()).isEqualTo(3);
        for (int i = 0; i < 3; i++) {
            CaseLabelNode label = first.labels().get(i);
            assertThat(label.kind()).isEqualTo(AstKind.CASE_LABEL);
            IntLiteralNode literal = (IntLiteralNode) ((ConstantCaseLabelNode) label).expression();
            assertThat(literal.lexeme()).isEqualTo(Integer.toString(i + 1));
        }
    }

    @Test
    public void eachCaseBodyIsAnImplicitBlock() {
        CompilationUnitNode unit = parseOk("s.sol", """
                func run(value: Int): Unit {
                    switch (value) {
                        case 1:
                            print("one")
                            print("again")
                        default:
                    }
                }
                """);
        SwitchStmtNode statement = switchInFunction(unit, "run");
        assertThat(statement.cases().get(0).body().kind()).isEqualTo(AstKind.BLOCK);
        assertThat(statement.cases().get(0).body().statements().size()).isEqualTo(2);
        assertThat(statement.cases().get(0).body().statements().get(0) instanceof ExprStmtNode).isTrue();
        assertThat(statement.cases().get(1).body().statements().isEmpty()).isTrue();
    }

    @Test
    public void stringConstantsAndNullAreConstantLabels() {
        CompilationUnitNode unit = parseOk("s.sol", """
                func run(value: String?): Unit {
                    switch (value) {
                        case null:
                            print("none")
                        case "a":
                            print("a")
                        default:
                            print("other")
                    }
                }
                """);
        SwitchStmtNode statement = switchInFunction(unit, "run");
        assertThat(((ConstantCaseLabelNode) statement.cases().get(0).labels().get(0)).expression().kind()).isEqualTo(AstKind.NULL_LITERAL);
        StringLiteralNode literal = (StringLiteralNode) ((ConstantCaseLabelNode) statement.cases().get(1).labels().get(0)).expression();
        assertThat(literal.lexeme()).isEqualTo("\"a\"");
    }

    @Test
    public void rawAndNormalRegexPatternsAreRecorded() {
        CompilationUnitNode unit = parseOk("s.sol", """
                func run(input: String): Unit {
                    switch (input) {
                        case regex r#"^\\d+$"#:
                            print("number")
                        case regex "x.*":
                            print("x")
                        default:
                            print("other")
                    }
                }
                """);
        SwitchStmtNode statement = switchInFunction(unit, "run");
        RegexCaseLabelNode raw = (RegexCaseLabelNode) statement.cases().get(0).labels().get(0);
        assertThat(raw.kind()).isEqualTo(AstKind.REGEX_CASE_LABEL);
        assertThat(((RawStringLiteralNode) raw.pattern()).value()).isEqualTo("^\\d+$");
        RegexCaseLabelNode normal = (RegexCaseLabelNode) statement.cases().get(1).labels().get(0);
        assertThat(((StringLiteralNode) normal.pattern()).lexeme().substring(1, 4)).isEqualTo("x.*");
    }

    @Test
    public void switchNestsInsideBlocksAndOtherSwitches() {
        CompilationUnitNode unit = parseOk("s.sol", """
                func run(a: Int, b: Int): Unit {
                    if (a > 0) {
                        switch (a) {
                            case 1:
                                switch (b) {
                                    case 2:
                                        print("nested")
                                    default:
                                        print("inner")
                                }
                            default:
                                print("outer")
                        }
                    }
                }
                """);
        SwitchStmtNode outer = (SwitchStmtNode) ((org.solvik.ast.statement.IfStmtNode) ((FunctionDeclNode) unit.declarations().get(0)).body().statements().get(0)).thenBlock().statements().get(0);
        SwitchStmtNode inner = (SwitchStmtNode) outer.cases().get(0).body().statements().get(0);
        assertThat(inner.cases().size()).isEqualTo(2);
    }

    @Test
    public void switchStatementSpanCoversTheWholeConstruct() {
        String text = """
                func run(value: Int): Unit {
                    switch (value) {
                        case 1:
                            print("one")
                        default:
                            print("other")
                    }
                }
                """;
        CompilationUnitNode unit = parseOk("s.sol", text);
        SwitchStmtNode statement = switchInFunction(unit, "run");
        String slice = text.substring(statement.span().startOffset(), statement.span().endOffset());
        assertThat(slice.startsWith("switch (value) {")).isTrue();
        assertThat(slice.endsWith("}")).isTrue();
    }

    @Test
    public void aSwitchWithoutAScrutineeIsRejected() {
        assertThat(parseFails("s.sol", """
                func run(): Unit {
                    switch {
                        default:
                            print("x")
                    }
                }
                """).hasErrors()).isTrue();
    }

    @Test
    public void aCaseWithoutAColonIsRejected() {
        assertThat(parseFails("s.sol", """
                func run(value: Int): Unit {
                    switch (value) {
                        case 1
                            print("one")
                    }
                }
                """).hasErrors()).isTrue();
    }

    @Test
    public void aCaseWithoutALabelIsRejected() {
        assertThat(parseFails("s.sol", """
                func run(value: Int): Unit {
                    switch (value) {
                        case:
                            print("one")
                    }
                }
                """).hasErrors()).isTrue();
    }

    @Test
    public void aRegexCaseWithoutAPatternIsRejected() {
        assertThat(parseFails("s.sol", """
                func run(input: String): Unit {
                    switch (input) {
                        case regex:
                            print("x")
                    }
                }
                """).hasErrors()).isTrue();
    }

    @Test
    public void aTrailingCommaInCaseLabelsIsRejected() {
        assertThat(parseFails("s.sol", """
                func run(value: Int): Unit {
                    switch (value) {
                        case 1,:
                            print("one")
                    }
                }
                """).hasErrors()).isTrue();
    }
}
