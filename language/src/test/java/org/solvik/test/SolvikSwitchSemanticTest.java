/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.parseOk;

import org.junit.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.statement.ConstantCaseLabelNode;
import org.solvik.ast.statement.RegexCaseLabelNode;
import org.solvik.ast.statement.SwitchStmtNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.IntType;
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
        assertTrue("analysis must succeed: " + result.diagnostics().all(), result.isSuccess());
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
                fun run(value: Int): Unit {
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
        assertSame(IntType.INSTANCE, type);
    }

    @Test
    public void groupedLabelsAndNegativeConstantsAreAccepted() {
        CheckedProgram program = check("""
                fun run(value: Int): Unit {
                    switch (value) {
                        case -1, 0, 1:
                            print("unit")
                        default:
                            print("other")
                    }
                }
                """);
        SwitchStmtNode statement = switchInFunction(program, "run");
        assertEquals(3, statement.cases().get(0).labels().size());
        assertSame(IntType.INSTANCE, program.typeOf(((ConstantCaseLabelNode) statement.cases().get(0).labels().get(0)).expression()).orElseThrow());
    }

    @Test
    public void longConstantsRequireALongScrutinee() {
        CheckedProgram program = check("""
                fun run(value: Long): Unit {
                    switch (value) {
                        case 1L:
                            print("one")
                        default:
                            print("other")
                    }
                }
                """);
        ConstantCaseLabelNode label = (ConstantCaseLabelNode) switchInFunction(program, "run").cases().get(0).labels().get(0);
        assertSame(LongType.INSTANCE, program.typeOf(label.expression()).orElseThrow());
    }

    @Test
    public void aNullLabelMatchesAGNullableScrutinee() {
        CheckedProgram program = check("""
                fun run(value: String?): Unit {
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
        assertSame(AstKind.NULL_LITERAL, ((ConstantCaseLabelNode) statement.cases().get(0).labels().get(0)).expression().kind());
        assertTrue(((ConstantCaseLabelNode) statement.cases().get(1).labels().get(0)).expression() instanceof org.solvik.ast.expression.StringLiteralNode);
    }

    @Test
    public void aRegexCaseCompilesItsConstantPatternOnce() {
        CheckedProgram program = check("""
                fun run(input: String): Unit {
                    switch (input) {
                        case regex r#"^\\d+$"#:
                            print("number")
                        default:
                            print("other")
                    }
                }
                """);
        RegexCaseLabelNode label = (RegexCaseLabelNode) switchInFunction(program, "run").cases().get(0).labels().get(0);
        assertEquals("^\\d+$", program.regexCasePatternOf(label).orElseThrow().source());
    }

    @Test
    public void aRegexCaseWorksOnAConstantStringPatternToo() {
        CheckedProgram program = check("""
                fun run(input: String): Unit {
                    switch (input) {
                        case regex "[a-z]+":
                            print("word")
                        default:
                            print("other")
                    }
                }
                """);
        RegexCaseLabelNode label = (RegexCaseLabelNode) switchInFunction(program, "run").cases().get(0).labels().get(0);
        assertEquals("[a-z]+", program.regexCasePatternOf(label).orElseThrow().source());
    }

    @Test
    public void anEmptyDefaultIsAccepted() {
        CheckedProgram program = check("""
                fun run(value: Int): Unit {
                    switch (value) {
                        case 1:
                            print("one")
                        default:
                    }
                }
                """);
        assertEquals(2, switchInFunction(program, "run").cases().size());
    }

    @Test
    public void aBreakInsideALoopNestedInACaseIsAccepted() {
        check("""
                fun run(value: Int): Unit {
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
                fun run(value: Int): Unit {
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
                fun run(value: String): Unit {
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
        assertSame(StringType.INSTANCE, program.typeOf(switchInFunction(program, "run").scrutinee()).orElseThrow());
    }
}
