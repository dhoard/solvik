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
import static org.junit.Assert.fail;

import java.util.List;
import org.junit.Test;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.diagnostic.DiagnosticSeverity;
import org.solvik.parser.SolvikParseResult;
import org.solvik.source.SourceFile;
import org.solvik.source.SourceSpan;

/**
 * Negative parser tests: malformed declarations, statement-continuation boundaries where
 * semicolon insertion cannot help, rejected SimpleLanguage syntax, out-of-scope syntax, and the
 * guarantee that any error yields no AST.
 */
public final class SolvikParserNegativeTest {

    private static DiagnosticBag expectErrors(String name, String text) {
        SolvikParseResult result = org.solvik.parser.SolvikParser.parse(new SourceFile(name, text));
        assertFalse("parse must fail: " + text, result.isSuccess());
        assertTrue("failed parse must carry at least one error", result.diagnostics().hasErrors());
        assertTrue("failed parse must expose no AST", result.ast().isEmpty());
        try {
            result.requireAst();
            fail("requireAst() must reject failed parses");
        } catch (IllegalStateException expected) {
            // expected
        }
        for (Diagnostic d : result.diagnostics().all()) {
            assertEquals(DiagnosticSeverity.ERROR, d.severity());
            assertTrue("span within source bounds: " + d.span(), d.span().endOffset() <= text.length());
        }
        return result.diagnostics();
    }

    private static Diagnostic first(DiagnosticBag bag) {
        List<Diagnostic> all = bag.all();
        assertFalse(all.isEmpty());
        return all.get(0);
    }

    @Test
    public void simpleLanguageFunctionKeywordIsRejectedAsLegacy() {
        DiagnosticBag bag = expectErrors("legacy.sol", "function foo(x) {\n  return x;\n}\n");
        Diagnostic d = first(bag);
        assertEquals(DiagnosticCode.PARSER_UNSUPPORTED_LEGACY_SYNTAX, d.code());
        assertEquals("SOLV-PARS-004", d.code().stableCode());
        assertEquals(SourceSpan.of(0, "function".length()), d.span());
        assertEquals("'func'", d.expected().orElseThrow());
        assertEquals("'function'", d.found().orElseThrow());
    }

    @Test
    public void slStyleDynamicFunctionIsRejected() {
        expectErrors("legacy2.sol", "function add(a, b) {\n  return a + b;\n}\n");
    }

    /**
     * The former function keyword is no longer reserved, so a declaration that uses it is rejected
     * as a normal parse error rather than being accepted through a compatibility path.
     */
    @Test
    public void formerKeywordIsRejectedAsADeclaration() {
        expectErrors("former.sol", "fun f(): Unit {\n}\n");
    }

    @Test
    public void unterminatedStatementInsideUnclosedParenIsRejected() {
        // Unmatched '(' keeps suppressing insertion, so the statement cannot terminate at `}`.
        DiagnosticBag bag = expectErrors("nosemi.sol", "func f(): Int {\n    val x: Int = (1\n}\n");
        assertTrue(first(bag).code() == DiagnosticCode.PARSER_UNEXPECTED_TOKEN || first(bag).code() == DiagnosticCode.PARSER_INCOMPLETE_INPUT);
    }

    @Test
    public void statementsConcatenatedOnOneLineAreRejected() {
        // No newline and no ';': insertion has no boundary to act on.
        expectErrors("nosemi2.sol", "func f(): Unit {\n    g(1) h(2)\n}\n");
    }

    @Test
    public void lineEndingInOperatorIsNotTerminated() {
        // `+` is not an eligible terminator, so the expression runs into `}` and fails.
        DiagnosticBag bag = expectErrors("nosemi3.sol", "func f(a: Int): Int {\n    return a +\n}\n");
        assertEquals(DiagnosticCode.PARSER_UNEXPECTED_TOKEN, first(bag).code());
    }

    @Test
    public void functionWithoutReturnTypeIsRejected() {
        expectErrors("ret.sol", "func f() {\n    return;\n}\n");
    }

    @Test
    public void untypedParameterIsRejected() {
        expectErrors("param.sol", "func f(a) : Unit {\n    return;\n}\n");
    }

    @Test
    public void parameterWithoutNameIsRejected() {
        expectErrors("param2.sol", "func f(: Int): Unit {\n    return;\n}\n");
    }

    @Test
    public void parameterMissingColonIsRejected() {
        expectErrors("param3.sol", "func f(a Int): Unit {\n    return;\n}\n");
    }

    @Test
    public void assignmentIsNotAnExpression() {
        // Assignment is a statement form only; using it inside an expression is a parse error.
        expectErrors("assign.sol", "func f(x: Int): Int {\n    val y = (x = 1);\n    return y;\n}\n");
    }

    @Test
    public void unterminatedBlockIsReportedAsIncompleteInput() {
        DiagnosticBag bag = expectErrors("eof.sol", "func f(): Unit {\n    return;\n");
        assertEquals(DiagnosticCode.PARSER_INCOMPLETE_INPUT, first(bag).code());
    }

    @Test
    public void truncatedReturnAtEofIsIncomplete() {
        DiagnosticBag bag = expectErrors("eof3.sol", "func f(): Int {\n    return ");
        assertEquals(DiagnosticCode.PARSER_INCOMPLETE_INPUT, first(bag).code());
    }

    @Test
    public void unclosedCallIsRejected() {
        expectErrors("eof4.sol", "func f(): Unit {\n    g(1;\n}\n");
    }

    @Test
    public void invalidCharacterProducesLexerError() {
        String src = "func f(): Unit {\n    @\n}\n";
        DiagnosticBag bag = expectErrors("lex.sol", src);
        Diagnostic d = first(bag);
        assertEquals(DiagnosticCode.LEXER_ERROR, d.code());
        assertEquals("SOLV-LEX-001", d.code().stableCode());
        int at = src.indexOf('@');
        assertEquals(SourceSpan.of(at, at + 1), d.span());
    }

    @Test
    public void unterminatedStringIsRejected() {
        String src = "func f(): Unit {\n    val s = \"oops;\n}\n";
        DiagnosticBag bag = expectErrors("str.sol", src);
        for (Diagnostic d : bag.all()) {
            assertTrue("expected lexical or parser diagnostic, got " + d.code(), //
                    d.code() == DiagnosticCode.LEXER_ERROR || d.code().stableCode().startsWith("SOLV-PARS"));
        }
    }

    @Test
    public void keywordUsedAsParameterNameIsRejected() {
        expectErrors("kw.sol", "func f(if: Int): Unit {\n    return;\n}\n");
    }

    /** Malformed inputs never yield a partial AST, regardless of where they break. */
    @Test
    public void errorResultsNeverExposePartialAst() {
        String[] bad = { //
                "func", //
                "func f", //
                "func f(", //
                "func f(:){", //
                "func f(): Unit { val }", //
                "func f(): Unit { 1 + }", //
                "func f(): Unit {} func", //
        };
        for (String src : bad) {
            SolvikParseResult r = org.solvik.parser.SolvikParser.parse(new SourceFile("bad.sol", src));
            assertFalse("must fail: [" + src + "]", r.isSuccess());
            assertTrue("no ast on failure: [" + src + "]", r.ast().isEmpty());
            assertTrue("errors present: [" + src + "]", r.diagnostics().hasErrors());
        }
    }

    @Test
    public void constructorModifiersAreRejected() {
        // A constructor is not a method, so it carries no `open`/`override` modifier.
        expectErrors("openctor.sol", "class C {\n    open C() {\n    }\n}\n");
        expectErrors("overridector.sol", "class C {\n    override C() {\n    }\n}\n");
    }

    @Test
    public void successfulParsesCarryNoDiagnostics() {
        String ok = "func f(a: Int): Int {\n    val t: Int = a * 2;\n    if (true) {\n        g(t);\n    } else {\n        h(t, obj.f);\n    }\n    return t;\n}\n";
        SolvikParseResult r = org.solvik.parser.SolvikParser.parse(new SourceFile("ok.sol", ok));
        assertTrue(r.isSuccess());
        assertTrue(r.diagnostics().isEmpty());
    }
}
