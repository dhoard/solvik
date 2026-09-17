/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.parseFails;
import static org.solvik.test.SolvikTestSupport.parseOk;

import org.junit.Test;
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
        assertEquals(AstKind.SWITCH_STMT, statement.kind());
        assertEquals(AstKind.NAME_REF_EXPR, statement.scrutinee().kind());
        assertEquals("value", ((NameRefExprNode) statement.scrutinee()).name());
        assertEquals(3, statement.cases().size());
        assertFalse(statement.cases().get(0).isDefault());
        assertFalse(statement.cases().get(1).isDefault());
        assertTrue(statement.cases().get(2).isDefault());
        assertTrue(statement.cases().get(2).labels().isEmpty());
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
        assertEquals(3, first.labels().size());
        for (int i = 0; i < 3; i++) {
            CaseLabelNode label = first.labels().get(i);
            assertEquals(AstKind.CASE_LABEL, label.kind());
            IntLiteralNode literal = (IntLiteralNode) ((ConstantCaseLabelNode) label).expression();
            assertEquals(Integer.toString(i + 1), literal.lexeme());
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
        assertEquals(AstKind.BLOCK, statement.cases().get(0).body().kind());
        assertEquals(2, statement.cases().get(0).body().statements().size());
        assertTrue(statement.cases().get(0).body().statements().get(0) instanceof ExprStmtNode);
        assertTrue(statement.cases().get(1).body().statements().isEmpty());
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
        assertEquals(AstKind.NULL_LITERAL, ((ConstantCaseLabelNode) statement.cases().get(0).labels().get(0)).expression().kind());
        StringLiteralNode literal = (StringLiteralNode) ((ConstantCaseLabelNode) statement.cases().get(1).labels().get(0)).expression();
        assertEquals("\"a\"", literal.lexeme());
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
        assertEquals(AstKind.REGEX_CASE_LABEL, raw.kind());
        assertEquals("^\\d+$", ((RawStringLiteralNode) raw.pattern()).value());
        RegexCaseLabelNode normal = (RegexCaseLabelNode) statement.cases().get(1).labels().get(0);
        assertEquals("x.*", ((StringLiteralNode) normal.pattern()).lexeme().substring(1, 4));
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
        assertEquals(2, inner.cases().size());
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
        assertTrue(slice.startsWith("switch (value) {"));
        assertTrue(slice.endsWith("}"));
    }

    @Test
    public void aSwitchWithoutAScrutineeIsRejected() {
        assertTrue(parseFails("s.sol", """
                func run(): Unit {
                    switch {
                        default:
                            print("x")
                    }
                }
                """).hasErrors());
    }

    @Test
    public void aCaseWithoutAColonIsRejected() {
        assertTrue(parseFails("s.sol", """
                func run(value: Int): Unit {
                    switch (value) {
                        case 1
                            print("one")
                    }
                }
                """).hasErrors());
    }

    @Test
    public void aCaseWithoutALabelIsRejected() {
        assertTrue(parseFails("s.sol", """
                func run(value: Int): Unit {
                    switch (value) {
                        case:
                            print("one")
                    }
                }
                """).hasErrors());
    }

    @Test
    public void aRegexCaseWithoutAPatternIsRejected() {
        assertTrue(parseFails("s.sol", """
                func run(input: String): Unit {
                    switch (input) {
                        case regex:
                            print("x")
                    }
                }
                """).hasErrors());
    }

    @Test
    public void aTrailingCommaInCaseLabelsIsRejected() {
        assertTrue(parseFails("s.sol", """
                func run(value: Int): Unit {
                    switch (value) {
                        case 1,:
                            print("one")
                    }
                }
                """).hasErrors());
    }
}
