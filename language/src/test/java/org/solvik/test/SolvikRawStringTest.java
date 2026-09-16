/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.assertNode;
import static org.solvik.test.SolvikTestSupport.body;
import static org.solvik.test.SolvikTestSupport.local;
import static org.solvik.test.SolvikTestSupport.onlyFunction;
import static org.solvik.test.SolvikTestSupport.parseFails;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;
import org.junit.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.RawStringLiteralNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.parser.SemicolonInsertingTokenSource;
import org.solvik.parser.generated.SolvikLexer;
import org.solvik.source.SourceSpan;

/**
 * Phase 3 tests for Rust-style raw strings (docs/LANGUAGE_SPEC.md section 15): lexer token shape,
 * counted delimiters, exact semantic values, precise unterminated diagnostics, and the absence of
 * any interaction between raw-string internal newlines and semicolon insertion.
 */
public final class SolvikRawStringTest {

    // --- helpers ---------------------------------------------------------------------------

    private static List<Token> lex(String src) {
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString(src));
        lexer.removeErrorListeners();
        return new ArrayList<>(lexer.getAllTokens());
    }

    /** Default-channel tokens after semicolon insertion; synthesized semicolons render as `~;~`. */
    private static String rendered(String src) {
        SolvikLexer lexer = new SolvikLexer(CharStreams.fromString(src));
        lexer.removeErrorListeners();
        SemicolonInsertingTokenSource stream = new SemicolonInsertingTokenSource(lexer);
        StringBuilder sb = new StringBuilder();
        while (true) {
            Token t = stream.nextToken();
            if (t.getChannel() == Token.DEFAULT_CHANNEL) {
                if (SemicolonInsertingTokenSource.isSyntheticSemi(t)) {
                    sb.append("~;~");
                } else if (t.getType() == Token.EOF) {
                    sb.append("<EOF>");
                } else {
                    sb.append(t.getText());
                }
                sb.append(' ');
            }
            if (t.getType() == Token.EOF) {
                return sb.toString().trim();
            }
        }
    }

    private static int countType(List<Token> tokens, int type) {
        return (int) tokens.stream().filter(t -> t.getType() == type).count();
    }

    private static Diagnostic firstOfCode(DiagnosticBag bag, DiagnosticCode code) {
        return bag.all().stream().filter(d -> d.code() == code).findFirst().orElseThrow(() -> new AssertionError("missing " + code + " in " + bag.all()));
    }

    private static RawStringLiteralNode rawLiteral(String src, int index) {
        CompilationUnitNode cu = parseOk("raw.sol", src);
        LocalDeclNode decl = local(onlyFunction(cu), index);
        return (RawStringLiteralNode) decl.initializer();
    }

    // --- lexer -----------------------------------------------------------------------------

    /** The three headline examples each lex to exactly one raw-string token holding the lexeme. */
    @Test
    public void specExamplesAreSingleRawStringTokens() {
        String[] lexemes = {"r\"abc\"", "r#\"Test '\"#", "r##\"contains \"# text\"##"};
        for (String lexeme : lexemes) {
            List<Token> toks = lex(lexeme);
            assertEquals("one token for " + lexeme, 1, toks.size());
            assertEquals(SolvikLexer.RAW_STRING_LITERAL, toks.get(0).getType());
            assertEquals(lexeme, toks.get(0).getText());
            assertEquals(0, toks.get(0).getStartIndex());
            assertEquals(lexeme.length() - 1, toks.get(0).getStopIndex());
        }
    }

    /** Empty raw strings of every hash count are valid and distinct from an identifier. */
    @Test
    public void emptyRawStringsOfEveryHashCount() {
        assertEquals(1, lex("r\"\"").size());
        assertEquals(1, lex("r#\"\"#").size());
        assertEquals(1, lex("r##\"\"##").size());
        assertEquals(1, lex("r###\"\"###").size());
        for (String src : List.of("r\"\"", "r#\"\"#", "r##\"\"##")) {
            assertEquals(SolvikLexer.RAW_STRING_LITERAL, lex(src).get(0).getType());
        }
    }

    /** Backslashes are ordinary characters: no escape is processed. */
    @Test
    public void backslashesArePreservedVerbatim() {
        String src = "r\"C:\\temp\\new\"";
        List<Token> toks = lex(src);
        assertEquals(1, toks.size());
        assertEquals(src, toks.get(0).getText());
        assertEquals("C:\\temp\\new", rawLiteral("fun f(): Unit {\n    val s = " + src + "\n}\n", 0).value());
    }

    /** Quotes are allowed inside a raw string as long as they are not the exact closing delimiter. */
    @Test
    public void embeddedQuotesAndHashesAreContent() {
        assertEquals("Test '", rawLiteral("fun f(): Unit {\n    val s = r#\"Test '\"#\n}\n", 0).value());
        assertEquals("contains \"# text", rawLiteral("fun f(): Unit {\n    val s = r##\"contains \"# text\"##\n}\n", 0).value());
        assertEquals("a\"b", rawLiteral("fun f(): Unit {\n    val s = r#\"a\"b\"#\n}\n", 0).value());
    }

    /** Physical newlines stay inside one token; the lexer never emits NEWLINE from raw content. */
    @Test
    public void multilineRawStringIsOneTokenWithoutNewlineTokens() {
        String src = "r#\"line one\nline two\nline three\"#";
        List<Token> toks = lex(src);
        assertEquals(1, toks.size());
        assertEquals(SolvikLexer.RAW_STRING_LITERAL, toks.get(0).getType());
        assertEquals(src, toks.get(0).getText());
        assertEquals("the three physical newlines are inside the literal", 0, countType(toks, SolvikLexer.NEWLINE));
    }

    /** An `r` not followed by `#`* and a quote is an ordinary identifier. */
    @Test
    public void identifiersBeginningWithRAreNotRawStrings() {
        List<Token> toks = lex("r raw rust r2 _r");
        assertEquals(5, toks.size());
        for (Token t : toks) {
            assertEquals(t.getText(), SolvikLexer.Identifier, t.getType());
        }
        // `r#` with no following quote is also not a raw string; `r` stays an identifier.
        assertEquals(SolvikLexer.Identifier, lex("r#").get(0).getType());
    }

    // --- diagnostics -----------------------------------------------------------------------

    /** An unterminated raw string reports at the opening delimiter and names `"` for N = 0. */
    @Test
    public void unterminatedRawStringWithoutHashesNamesPlainQuote() {
        String src = "fun f(): Unit {\n    val s = r\"oops\n}\n";
        DiagnosticBag bag = parseFails("unterminated0.sol", src);
        Diagnostic d = firstOfCode(bag, DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING);
        assertEquals("SOLV-LEX-002", d.code().stableCode());
        assertEquals("\"", d.expected().orElseThrow());
        int open = src.indexOf("r\"");
        assertEquals(SourceSpan.of(open, open + 2), d.span());
        assertTrue(d.message(), d.message().contains("expected closing delimiter"));
    }

    /** An unterminated counted raw string names the exact `"` + `#`* closing delimiter. */
    @Test
    public void unterminatedCountedRawStringNamesExpectedDelimiter() {
        String src = "fun f(): Unit {\n    val s = r###\"oops\"##\n}\n";
        DiagnosticBag bag = parseFails("unterminated3.sol", src);
        Diagnostic d = firstOfCode(bag, DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING);
        assertEquals("\"###", d.expected().orElseThrow());
        int open = src.indexOf("r###\"");
        assertEquals(SourceSpan.of(open, open + 5), d.span());
    }

    /** Closing with fewer hashes than the opening delimiter is a mismatch, not a terminator. */
    @Test
    public void closingDelimiterWithTooFewHashesIsUnterminated() {
        String src = "fun f(): Unit {\n    val s = r##\"abc\"#\n}\n";
        DiagnosticBag bag = parseFails("mismatch-few.sol", src);
        assertEquals("\"##", firstOfCode(bag, DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING).expected().orElseThrow());
    }

    /** Closing with more hashes than the opening delimiter is a mismatch, not a terminator. */
    @Test
    public void closingDelimiterWithTooManyHashesIsUnterminated() {
        String src = "fun f(): Unit {\n    val s = r#\"abc\"##\n}\n";
        DiagnosticBag bag = parseFails("mismatch-many.sol", src);
        assertEquals("\"#", firstOfCode(bag, DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING).expected().orElseThrow());
    }

    /** A failed raw string yields no AST, like every other lexical failure. */
    @Test
    public void unterminatedRawStringExposesNoAst() {
        DiagnosticBag bag = parseFails("noast.sol", "fun f(): Unit {\n    val s = r\"abc\n}\n");
        assertTrue(bag.hasErrors());
    }

    // --- parser / AST ----------------------------------------------------------------------

    /** A raw literal carries its exact lexeme, count, semantic value, and span. */
    @Test
    public void rawStringAstNodeCarriesLexemeHashCountAndValue() {
        String src = "fun f(): Unit {\n    val s = r##\"contains \"# text\"##\n}\n";
        RawStringLiteralNode node = rawLiteral(src, 0);
        assertEquals(AstKind.RAW_STRING_LITERAL, node.kind());
        assertEquals("r##\"contains \"# text\"##", node.lexeme());
        assertEquals("contains \"# text", node.value());
        assertEquals(2, node.hashCount());
        assertNode(node, AstKind.RAW_STRING_LITERAL, src, "r##\"contains \"# text\"##");
    }

    /** The zero-hash form reports count zero and strips only the plain quotes. */
    @Test
    public void zeroHashRawStringNode() {
        RawStringLiteralNode node = rawLiteral("fun f(): Unit {\n    val s = r\"abc\"\n}\n", 0);
        assertEquals(0, node.hashCount());
        assertEquals("abc", node.value());
    }

    /** Raw strings work in every expression position the Phase 1 grammar supports. */
    @Test
    public void rawStringsAppearInLocalInitializersAndCallArguments() {
        String src = "fun f(): Unit {\n    val a = r\"x\"\n    g(r#\"y\"#, a)\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("positions.sol", src));
        assertEquals(2, body(fn).statements().size());
        assertEquals("x", ((RawStringLiteralNode) local(fn, 0).initializer()).value());
        assertEquals("y", ((RawStringLiteralNode) body(fn).statements().get(1).children().get(0).children().get(1)).value());
    }

    /** LANGUAGE_SPEC regex/JSON/path/SQL examples parse and keep their exact contents. */
    @Test
    public void specRegexJsonPathAndSqlExamples() {
        String regex = "r#\"\\d+\\s+\"#";
        assertEquals("\\d+\\s+", rawLiteral("fun f(): Unit {\n    val s = " + regex + "\n}\n", 0).value());

        String json = "r#\"{\"name\":\"Doug\",\"path\":\"C:\\temp\"}\"#";
        assertEquals("{\"name\":\"Doug\",\"path\":\"C:\\temp\"}", rawLiteral("fun f(): Unit {\n    val s = " + json + "\n}\n", 0).value());

        String path = "r\"C:\\temp\\new\"";
        assertEquals("C:\\temp\\new", rawLiteral("fun f(): Unit {\n    val s = " + path + "\n}\n", 0).value());

        String sqlLexeme = "r#\"\nSELECT *\nFROM users\nWHERE name = 'Doug'\n\"#";
        String sqlSrc = "fun f(): Unit {\n    val s = " + sqlLexeme + "\n}\n";
        assertEquals("\nSELECT *\nFROM users\nWHERE name = 'Doug'\n", rawLiteral(sqlSrc, 0).value());
    }

    // --- semicolon insertion ---------------------------------------------------------------

    /**
     * Newlines inside a raw string are part of one token, so the token stream sees exactly the two
     * statement boundaries outside it and never a boundary inside it.
     */
    @Test
    public void rawStringInternalNewlinesAreInvisibleToSemicolonInsertion() {
        String src = "val s = r#\"a\nb\nc\"#\nval t = 2\n";
        assertEquals("val s = r#\"a\nb\nc\"# ~;~ val t = 2 ~;~ <EOF>", rendered(src));
    }

    /** A physical newline directly after a raw string terminates the statement like any literal. */
    @Test
    public void newlineAfterRawStringTerminatesTheStatement() {
        String src = "fun f(): Int {\n    val s = r\"abc\"\n    val t = 2\n    return t\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("after.sol", src));
        assertEquals(3, body(fn).statements().size());
        assertEquals("abc", ((RawStringLiteralNode) local(fn, 0).initializer()).value());
    }

    /** A comment between a raw string and its line break does not disturb termination. */
    @Test
    public void commentAfterRawStringStillTerminates() {
        String src = "fun f(): Int {\n    val s = r\"abc\" // note\n    return 1\n}\n";
        assertEquals(2, body(onlyFunction(parseOk("comment.sol", src))).statements().size());
    }

    /** A raw string that ends at end of file still terminates its statement exactly once. */
    @Test
    public void rawStringAtEndOfFileTerminates() {
        assertEquals("val s = r\"abc\" ~;~ <EOF>", rendered("val s = r\"abc\""));
    }

    /** A raw string containing newlines is still one statement, not several. */
    @Test
    public void multilineRawStringKeepsOneStatementWithNewlinesInValue() {
        String src = "fun f(): Unit {\n    val s = r#\"first\nsecond\"#\n    g(s)\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("multiline.sol", src));
        assertEquals(2, body(fn).statements().size());
        assertEquals("first\nsecond", ((RawStringLiteralNode) local(fn, 0).initializer()).value());
    }

    /** Normal strings are unaffected: they still reject unescaped newlines. */
    @Test
    public void normalStringsRemainNewlineFree() {
        parseFails("normal.sol", "fun f(): Unit {\n    val s = \"a\nb\"\n}\n");
    }

    /** The raw-string prefix must be contiguous; whitespace between `r` and `#` is not raw. */
    @Test
    public void nonContiguousPrefixIsNotARawString() {
        // `r #"abc"#` lexes as Identifier `r`, then an invalid `#`, so the program is rejected.
        parseFails("contig.sol", "fun f(): Unit {\n    val s = r #\"abc\"#\n}\n");
    }
}
