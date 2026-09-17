/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.body;
import static org.solvik.test.SolvikTestSupport.local;
import static org.solvik.test.SolvikTestSupport.onlyFunction;
import static org.solvik.test.SolvikTestSupport.parseFails;
import static org.solvik.test.SolvikTestSupport.parseOk;
import static org.solvik.test.SolvikTestSupport.ret;

import java.util.List;
import org.junit.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.statement.ReturnStmtNode;

/**
 * Parser/AST-level Phase 2 tests. A newline-terminated program and its explicit-semicolon twin
 * must parse to the same tree shape; because synthesized semicolons sit at zero width right after
 * the terminated token, statement spans in the newline form end at the last real token, which the
 * first test pins with exact source slices. Negative cases pin boundaries where insertion must not
 * fire (dangling operators, concatenated statements, unclosed delimiters).
 */
public final class SolvikSemicolonInsertionTest {

    /** Parses both spellings and asserts identical tree shapes, returning the newline-form tree. */
    private static FunctionDeclNode parseEquivalent(String name, String newlineText, String explicitText) {
        CompilationUnitNode viaNewline = parseOk(name, newlineText);
        CompilationUnitNode viaExplicit = parseOk(name, explicitText);
        assertEquals("newline and semicolon forms must agree on tree shape", //
                viaExplicit.shapeTree(), viaNewline.shapeTree());
        return onlyFunction(viaNewline);
    }

    private static String slice(String source, org.solvik.ast.AstNode node) {
        return source.substring(node.span().startOffset(), node.span().endOffset());
    }

    /** LANGUAGE_SPEC.md section 16 headline example inside a function body. */
    @Test
    public void newlineLocalsMatchSemicolonLocals() {
        String newlines = "func f(): Int {\n    val x = 1\n    val y = 2\n    return x\n}\n";
        String explicit = "func f(): Int {\n    val x = 1;\n    val y = 2;\n    return x;\n}\n";
        FunctionDeclNode fn = parseEquivalent("asi1.sol", newlines, explicit);
        assertEquals("x", local(fn, 0).name());
        assertEquals("y", local(fn, 1).name());
        // Spans stop at the literal: neither the newline nor the synthesized semi is included.
        assertEquals("val x = 1", slice(newlines, local(fn, 0)));
        assertEquals("val y = 2", slice(newlines, local(fn, 1)));
        assertEquals("return x", slice(newlines, ret(fn, 2)));
    }

    /** Multiline expressions after operators survive intact (TEST_PLAN example). */
    @Test
    public void multilineExpressionAfterOperatorsIsOneStatement() {
        String newlines = "func f(price: Int, tax: Int, shipping: Int): Int {\n    val total = price +\n        tax +\n        shipping\n    return total\n}\n";
        String explicit = "func f(price: Int, tax: Int, shipping: Int): Int {\n    val total = price + tax + shipping;\n    return total;\n}\n";
        FunctionDeclNode fn = parseEquivalent("asi2.sol", newlines, explicit);
        assertEquals(2, body(fn).statements().size());
        assertEquals("val total = price +\n        tax +\n        shipping", slice(newlines, local(fn, 0)));
    }

    /** `return` followed by a newline terminates the return; the next line is a new statement. */
    @Test
    public void returnNewlineTerminatesTheReturn() {
        String newlines = "func f(value: Int): Unit {\n    return\n    value\n}\n";
        String explicit = "func f(value: Int): Unit {\n    return;\n    value;\n}\n";
        FunctionDeclNode fn = parseEquivalent("asi3.sol", newlines, explicit);
        List<?> statements = body(fn).statements();
        assertEquals(2, statements.size());
        ReturnStmtNode first = (ReturnStmtNode) statements.get(0);
        assertTrue("the return carries no value", first.value().isEmpty());
        assertEquals("return", slice(newlines, first));
        assertEquals("value", slice(newlines, (org.solvik.ast.AstNode) statements.get(1)));
    }

    /** Leading-dot chains fold into one member-chain expression (TEST_PLAN example). */
    @Test
    public void leadingDotChainParsesAsSingleExpression() {
        String newlines = "func f(service: Service): Result {\n    val result = service\n        .load()\n        .transform()\n    return result\n}\n";
        String explicit = "func f(service: Service): Result {\n    val result = service.load().transform();\n    return result;\n}\n";
        FunctionDeclNode fn = parseEquivalent("asi4.sol", newlines, explicit);
        CallExprNode outer = (CallExprNode) local(fn, 0).initializer();
        MemberAccessExprNode chain = (MemberAccessExprNode) outer.callee();
        assertEquals("transform", chain.memberName());
        assertEquals("service\n        .load()\n        .transform()", slice(newlines, local(fn, 0).initializer()));
    }

    /** `}` followed by `else` on the next line: the else stays attached to the if. */
    @Test
    public void danglingElseOnNextLineStaysAttached() {
        String newlines = "func f(c: Boolean): Unit {\n    if (c) {\n        g()\n    }\n    else {\n        h()\n    }\n}\n";
        String explicit = "func f(c: Boolean): Unit {\n    if (c) { g(); } else { h(); }\n}\n";
        FunctionDeclNode fn = parseEquivalent("asi5.sol", newlines, explicit);
        assertEquals(1, body(fn).statements().size());
    }

    /** Blank lines and comment-only lines between statements change nothing structural. */
    @Test
    public void blankAndCommentLinesDoNotChangeTheTree() {
        String spaced = "func f(): Int {\n\n    val x = 1\n\n    // a note\n\n    val y = 2\n\n    /* block\n       note */\n\n    return x\n\n}\n";
        String tight = "func f(): Int {\n    val x = 1;\n    val y = 2;\n    return x;\n}\n";
        FunctionDeclNode fn = parseEquivalent("asi6.sol", spaced, tight);
        assertEquals(3, body(fn).statements().size());
        assertEquals("val x = 1", slice(spaced, local(fn, 0)));
        assertEquals("val y = 2", slice(spaced, local(fn, 1)));
        assertEquals("return x", slice(spaced, ret(fn, 2)));
    }

    /** Call statements terminated by a newline, including at end of file with no trailing newline. */
    @Test
    public void expressionStatementsTerminateOnNewlineAndEof() {
        String src = "func f(): Unit {\n    g(1)\n    obj.store(2)\n}";
        FunctionDeclNode fn = parseEquivalent("asi7.sol", src, "func f(): Unit {\n    g(1);\n    obj.store(2);\n}");
        assertEquals(2, body(fn).statements().size());
        assertEquals("obj.store(2)", slice(src, body(fn).statements().get(1)));
    }

    /** Mixed explicit and newline termination composes without producing empty statements. */
    @Test
    public void mixedTerminationProducesNoEmptyStatements() {
        String src = "func f(): Int {\n    val x = 1;\n    val y = 2\n    val z = 3;;\n    return x\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("asi8.sol", src));
        assertEquals("standalone semis produce no AST statements", 4, body(fn).statements().size());
        assertEquals("explicit terminator belongs to the statement span", "val z = 3;", slice(src, local(fn, 2)));
    }

    /** Carriage-return line endings terminate identically to LF and keep valid spans. */
    @Test
    public void crlfSourceMatchesLfSource() {
        String lf = "func f(): Int {\n    val x = 1\n    return x\n}\n";
        String crlf = lf.replace("\n", "\r\n");
        FunctionDeclNode fromLf = parseEquivalent("asi9.sol", lf, lf.replace("\n", ";\n"));
        CompilationUnitNode fromCrlf = parseOk("asi9.sol", crlf);
        assertEquals(fromLf.shapeTree(), onlyFunction(fromCrlf).shapeTree());
        assertEquals("val x = 1", slice(crlf, local(onlyFunction(fromCrlf), 0)));
        assertEquals("return x", slice(crlf, ret(onlyFunction(fromCrlf), 1)));
    }

    /** Statements concatenated without a newline or `;` must stay rejected. */
    @Test
    public void statementsConcatenatedOnOneLineAreRejected() {
        parseFails("bad1.sol", "func f(): Unit {\n    g(1) h(2)\n}\n");
        parseFails("bad2.sol", "func f(): Unit {\n    val x = 1 val y = 2\n}\n");
    }

    /** A line ending in an operator continues: it can never terminate and so stays rejected. */
    @Test
    public void operatorBeforeNewlineCannotTerminate() {
        parseFails("bad3.sol", "func f(a: Int): Int {\n    return a +\n}\n");
        parseFails("bad4.sol", "func f(): Unit {\n    val x =\n    val y = 1\n}\n");
    }

    /** An unclosed `(` keeps suppressing insertion, so its statement never terminates. */
    @Test
    public void unclosedParenthesisNeverTerminatesAtEndOfFile() {
        parseFails("bad5.sol", "func f(): Int {\n    val x = (1\n}\n");
    }

    /** `?.` chains stay one statement and are accepted as safe member access from Phase 10. */
    @Test
    public void nullableChainStaysOneStatementAndParses() {
        String src = "func f(service: Service): Result {\n    val result = service\n        ?.load()\n    return result\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("nullable.sol", src));
        assertEquals(2, body(fn).statements().size());
        CallExprNode call = (CallExprNode) local(fn, 0).initializer();
        MemberAccessExprNode member = (MemberAccessExprNode) call.callee();
        assertTrue("the safe access must be marked safe", member.isSafe());
        assertEquals("load", member.memberName());
    }

    /** Bracket tokens exist for depth tracking only; bracket syntax is still rejected. */
    @Test
    public void bracketSyntaxIsLexedButStillRejected() {
        parseFails("brackets.sol", "func f(): Int {\n    val x = g[1]\n    return x\n}\n");
    }

    /** Programs with no explicit semicolons at all now parse purely through insertion. */
    @Test
    public void programsWithoutAnyExplicitSemicolonParse() {
        String src = "func add(a: Int, b: Int): Int {\n    val sum = a + b\n    return sum\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("asi10.sol", src));
        assertEquals(0, src.chars().filter(c -> c == ';').count());
        assertEquals(2, body(fn).statements().size());
        assertEquals("val sum = a + b", slice(src, local(fn, 0)));
    }

    /** Empty and whitespace-only inputs remain valid units under insertion. */
    @Test
    public void whitespaceOnlySourcesStillParse() {
        assertEquals(List.of(), parseOk("asi11.sol", "").declarations());
        assertEquals(List.of(), parseOk("asi12.sol", "\n\n// only a comment\n").declarations());
    }

    /** Standalone semicolons between declarations are tolerated and invisible in the AST. */
    @Test
    public void standaloneSemisBetweenDeclarationsAreIgnored() {
        CompilationUnitNode cu = parseOk("asi13.sol", ";;\nfunc a(): Unit {\n}\n;\nfunc b(): Unit {\n}\n;\n");
        assertEquals(2, cu.declarations().size());
        assertEquals("a", ((FunctionDeclNode) cu.declarations().get(0)).name());
        assertEquals("b", ((FunctionDeclNode) cu.declarations().get(1)).name());
        assertEquals(parseOk("asi13b.sol", "func a(): Unit {\n}\nfunc b(): Unit {\n}\n").shapeTree(), cu.shapeTree());
    }
}
