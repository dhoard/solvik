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
import org.junit.jupiter.api.Test;
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
            assertThat(toks.size()).as("one token for " + lexeme).isEqualTo(1);
            assertThat(toks.get(0).getType()).isEqualTo(SolvikLexer.RAW_STRING_LITERAL);
            assertThat(toks.get(0).getText()).isEqualTo(lexeme);
            assertThat(toks.get(0).getStartIndex()).isEqualTo(0);
            assertThat(toks.get(0).getStopIndex()).isEqualTo(lexeme.length() - 1);
        }
    }

    /** Empty raw strings of every hash count are valid and distinct from an identifier. */
    @Test
    public void emptyRawStringsOfEveryHashCount() {
        assertThat(lex("r\"\"").size()).isEqualTo(1);
        assertThat(lex("r#\"\"#").size()).isEqualTo(1);
        assertThat(lex("r##\"\"##").size()).isEqualTo(1);
        assertThat(lex("r###\"\"###").size()).isEqualTo(1);
        for (String src : List.of("r\"\"", "r#\"\"#", "r##\"\"##")) {
            assertThat(lex(src).get(0).getType()).isEqualTo(SolvikLexer.RAW_STRING_LITERAL);
        }
    }

    /** Backslashes are ordinary characters: no escape is processed. */
    @Test
    public void backslashesArePreservedVerbatim() {
        String src = "r\"C:\\temp\\new\"";
        List<Token> toks = lex(src);
        assertThat(toks.size()).isEqualTo(1);
        assertThat(toks.get(0).getText()).isEqualTo(src);
        assertThat(rawLiteral("func f(): Unit {\n    val s = " + src + "\n}\n", 0).value()).isEqualTo("C:\\temp\\new");
    }

    /** Quotes are allowed inside a raw string as long as they are not the exact closing delimiter. */
    @Test
    public void embeddedQuotesAndHashesAreContent() {
        assertThat(rawLiteral("func f(): Unit {\n    val s = r#\"Test '\"#\n}\n", 0).value()).isEqualTo("Test '");
        assertThat(rawLiteral("func f(): Unit {\n    val s = r##\"contains \"# text\"##\n}\n", 0).value()).isEqualTo("contains \"# text");
        assertThat(rawLiteral("func f(): Unit {\n    val s = r#\"a\"b\"#\n}\n", 0).value()).isEqualTo("a\"b");
    }

    /** Physical newlines stay inside one token; the lexer never emits NEWLINE from raw content. */
    @Test
    public void multilineRawStringIsOneTokenWithoutNewlineTokens() {
        String src = "r#\"line one\nline two\nline three\"#";
        List<Token> toks = lex(src);
        assertThat(toks.size()).isEqualTo(1);
        assertThat(toks.get(0).getType()).isEqualTo(SolvikLexer.RAW_STRING_LITERAL);
        assertThat(toks.get(0).getText()).isEqualTo(src);
        assertThat(countType(toks, SolvikLexer.NEWLINE)).as("the three physical newlines are inside the literal").isEqualTo(0);
    }

    /** An `r` not followed by `#`* and a quote is an ordinary identifier. */
    @Test
    public void identifiersBeginningWithRAreNotRawStrings() {
        List<Token> toks = lex("r raw rust r2 _r");
        assertThat(toks.size()).isEqualTo(5);
        for (Token t : toks) {
            assertThat(t.getType()).as(t.getText()).isEqualTo(SolvikLexer.Identifier);
        }
        // `r#` with no following quote is also not a raw string; `r` stays an identifier.
        assertThat(lex("r#").get(0).getType()).isEqualTo(SolvikLexer.Identifier);
    }

    // --- diagnostics -----------------------------------------------------------------------

    /** An unterminated raw string reports at the opening delimiter and names `"` for N = 0. */
    @Test
    public void unterminatedRawStringWithoutHashesNamesPlainQuote() {
        String src = "func f(): Unit {\n    val s = r\"oops\n}\n";
        DiagnosticBag bag = parseFails("unterminated0.sol", src);
        Diagnostic d = firstOfCode(bag, DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING);
        assertThat(d.code().stableCode()).isEqualTo("SOLV-LEX-002");
        assertThat(d.expected().orElseThrow()).isEqualTo("\"");
        int open = src.indexOf("r\"");
        assertThat(d.span()).isEqualTo(SourceSpan.of(open, open + 2));
        assertThat(d.message().contains("expected closing delimiter")).as(d.message()).isTrue();
    }

    /** An unterminated counted raw string names the exact `"` + `#`* closing delimiter. */
    @Test
    public void unterminatedCountedRawStringNamesExpectedDelimiter() {
        String src = "func f(): Unit {\n    val s = r###\"oops\"##\n}\n";
        DiagnosticBag bag = parseFails("unterminated3.sol", src);
        Diagnostic d = firstOfCode(bag, DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING);
        assertThat(d.expected().orElseThrow()).isEqualTo("\"###");
        int open = src.indexOf("r###\"");
        assertThat(d.span()).isEqualTo(SourceSpan.of(open, open + 5));
    }

    /** Closing with fewer hashes than the opening delimiter is a mismatch, not a terminator. */
    @Test
    public void closingDelimiterWithTooFewHashesIsUnterminated() {
        String src = "func f(): Unit {\n    val s = r##\"abc\"#\n}\n";
        DiagnosticBag bag = parseFails("mismatch-few.sol", src);
        assertThat(firstOfCode(bag, DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING).expected().orElseThrow()).isEqualTo("\"##");
    }

    /** Closing with more hashes than the opening delimiter is a mismatch, not a terminator. */
    @Test
    public void closingDelimiterWithTooManyHashesIsUnterminated() {
        String src = "func f(): Unit {\n    val s = r#\"abc\"##\n}\n";
        DiagnosticBag bag = parseFails("mismatch-many.sol", src);
        assertThat(firstOfCode(bag, DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING).expected().orElseThrow()).isEqualTo("\"#");
    }

    /** A failed raw string yields no AST, like every other lexical failure. */
    @Test
    public void unterminatedRawStringExposesNoAst() {
        DiagnosticBag bag = parseFails("noast.sol", "func f(): Unit {\n    val s = r\"abc\n}\n");
        assertThat(bag.hasErrors()).isTrue();
    }

    // --- parser / AST ----------------------------------------------------------------------

    // --- valid counted delimiters --------------------------------------------------------

    /** Every hash count 0..4 has a matching valid form that lexes to one raw-string token. */
    @Test
    public void validDelimitersOfEveryHashCount() {
        String[] srcs = {"r\"abc\"", "r#\"abc\"#", "r##\"abc\"##", "r###\"abc\"###", "r####\"abc\"####"};
        for (String src : srcs) {
            List<Token> toks = lex(src);
            assertThat(toks.size()).as("one token for " + src).isEqualTo(1);
            assertThat(toks.get(0).getType()).isEqualTo(SolvikLexer.RAW_STRING_LITERAL);
            assertThat(toks.get(0).getText()).isEqualTo(src);
            assertThat(rawLiteral("func f(): Unit {\n    val s = " + src + "\n}\n", 0).value()).isEqualTo("abc");
        }
    }

    /** Embedded quotes are content for every hash count, not a terminator. */
    @Test
    public void embeddedQuotesAreContentForEveryHashCount() {
        assertThat(rawLiteral("func f(): Unit {\n    val s = r#\"a\"b\"#\n}\n", 0).value()).isEqualTo("a\"b");
        assertThat(rawLiteral("func f(): Unit {\n    val s = r##\"a\"b\"##\n}\n", 0).value()).isEqualTo("a\"b");
        assertThat(rawLiteral("func f(): Unit {\n    val s = r###\"a\"b\"###\n}\n", 0).value()).isEqualTo("a\"b");
        assertThat(rawLiteral("func f(): Unit {\n    val s = r####\"a\"b\"####\n}\n", 0).value()).isEqualTo("a\"b");
    }

    /** Empty counted raw strings are valid for every hash count. */
    @Test
    public void emptyCountedRawStringsOfEveryHashCount() {
        for (String src : List.of("r#\"\"#", "r##\"\"##", "r###\"\"###", "r####\"\"####")) {
            assertThat(lex(src).size()).as("one token for " + src).isEqualTo(1);
            assertThat(lex(src).get(0).getType()).isEqualTo(SolvikLexer.RAW_STRING_LITERAL);
            assertThat(rawLiteral("func f(): Unit {\n    val s = " + src + "\n}\n", 0).value()).isEqualTo("");
        }
    }

    /** A raw string whose closing delimiter has the wrong hash count is not a terminator. */
    @Test
    public void headlineMismatchedDelimiterIsRejected() {
        // `r"#..."#` opens with N = 0 but the closing `"#` expects N = 1.
        String src = "func f(): Unit {\n    val s = r\"#abc\"#\n}\n";
        DiagnosticBag bag = parseFails("rquote-hash.sol", src);
        assertThat(bag.all().stream().filter(d -> d.code() == DiagnosticCode.LEXER_UNTERMINATED_RAW_STRING).findFirst())
            .as("expected SOLV-LEX-002 for the r\"#...\"# form")
            .isPresent();
    }

    /** A raw literal carries its exact lexeme, count, semantic value, and span. */
    @Test
    public void rawStringAstNodeCarriesLexemeHashCountAndValue() {
        String src = "func f(): Unit {\n    val s = r##\"contains \"# text\"##\n}\n";
        RawStringLiteralNode node = rawLiteral(src, 0);
        assertThat(node.kind()).isEqualTo(AstKind.RAW_STRING_LITERAL);
        assertThat(node.lexeme()).isEqualTo("r##\"contains \"# text\"##");
        assertThat(node.value()).isEqualTo("contains \"# text");
        assertThat(node.hashCount()).isEqualTo(2);
        assertNode(node, AstKind.RAW_STRING_LITERAL, src, "r##\"contains \"# text\"##");
    }

    /** The zero-hash form reports count zero and strips only the plain quotes. */
    @Test
    public void zeroHashRawStringNode() {
        RawStringLiteralNode node = rawLiteral("func f(): Unit {\n    val s = r\"abc\"\n}\n", 0);
        assertThat(node.hashCount()).isEqualTo(0);
        assertThat(node.value()).isEqualTo("abc");
    }

    /** Raw strings work in every expression position the Phase 1 grammar supports. */
    @Test
    public void rawStringsAppearInLocalInitializersAndCallArguments() {
        String src = "func f(): Unit {\n    val a = r\"x\"\n    g(r#\"y\"#, a)\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("positions.sol", src));
        assertThat(body(fn).statements().size()).isEqualTo(2);
        assertThat(((RawStringLiteralNode) local(fn, 0).initializer()).value()).isEqualTo("x");
        assertThat(((RawStringLiteralNode) body(fn).statements().get(1).children().get(0).children().get(1)).value()).isEqualTo("y");
    }

    /** LANGUAGE_SPEC regex/JSON/path/SQL examples parse and keep their exact contents. */
    @Test
    public void specRegexJsonPathAndSqlExamples() {
        String regex = "r#\"\\d+\\s+\"#";
        assertThat(rawLiteral("func f(): Unit {\n    val s = " + regex + "\n}\n", 0).value()).isEqualTo("\\d+\\s+");

        String json = "r#\"{\"name\":\"Doug\",\"path\":\"C:\\temp\"}\"#";
        assertThat(rawLiteral("func f(): Unit {\n    val s = " + json + "\n}\n", 0).value()).isEqualTo("{\"name\":\"Doug\",\"path\":\"C:\\temp\"}");

        String path = "r\"C:\\temp\\new\"";
        assertThat(rawLiteral("func f(): Unit {\n    val s = " + path + "\n}\n", 0).value()).isEqualTo("C:\\temp\\new");

        String sqlLexeme = "r#\"\nSELECT *\nFROM users\nWHERE name = 'Doug'\n\"#";
        String sqlSrc = "func f(): Unit {\n    val s = " + sqlLexeme + "\n}\n";
        assertThat(rawLiteral(sqlSrc, 0).value()).isEqualTo("\nSELECT *\nFROM users\nWHERE name = 'Doug'\n");
    }

    // --- semicolon insertion ---------------------------------------------------------------

    /**
     * Newlines inside a raw string are part of one token, so the token stream sees exactly the two
     * statement boundaries outside it and never a boundary inside it.
     */
    @Test
    public void rawStringInternalNewlinesAreInvisibleToSemicolonInsertion() {
        String src = "val s = r#\"a\nb\nc\"#\nval t = 2\n";
        assertThat(rendered(src)).isEqualTo("val s = r#\"a\nb\nc\"# ~;~ val t = 2 ~;~ <EOF>");
    }

    /** A physical newline directly after a raw string terminates the statement like any literal. */
    @Test
    public void newlineAfterRawStringTerminatesTheStatement() {
        String src = "func f(): Integer {\n    val s = r\"abc\"\n    val t = 2\n    return t\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("after.sol", src));
        assertThat(body(fn).statements().size()).isEqualTo(3);
        assertThat(((RawStringLiteralNode) local(fn, 0).initializer()).value()).isEqualTo("abc");
    }

    /** A comment between a raw string and its line break does not disturb termination. */
    @Test
    public void commentAfterRawStringStillTerminates() {
        String src = "func f(): Integer {\n    val s = r\"abc\" // note\n    return 1\n}\n";
        assertThat(body(onlyFunction(parseOk("comment.sol", src))).statements().size()).isEqualTo(2);
    }

    /** A raw string that ends at end of file still terminates its statement exactly once. */
    @Test
    public void rawStringAtEndOfFileTerminates() {
        assertThat(rendered("val s = r\"abc\"")).isEqualTo("val s = r\"abc\" ~;~ <EOF>");
    }

    /** A raw string containing newlines is still one statement, not several. */
    @Test
    public void multilineRawStringKeepsOneStatementWithNewlinesInValue() {
        String src = "func f(): Unit {\n    val s = r#\"first\nsecond\"#\n    g(s)\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("multiline.sol", src));
        assertThat(body(fn).statements().size()).isEqualTo(2);
        assertThat(((RawStringLiteralNode) local(fn, 0).initializer()).value()).isEqualTo("first\nsecond");
    }

    /** Normal strings are unaffected: they still reject unescaped newlines. */
    @Test
    public void normalStringsRemainNewlineFree() {
        parseFails("normal.sol", "func f(): Unit {\n    val s = \"a\nb\"\n}\n");
    }

    /** The raw-string prefix must be contiguous; whitespace between `r` and `#` is not raw. */
    @Test
    public void nonContiguousPrefixIsNotARawString() {
        // `r #"abc"#` lexes as Identifier `r`, then an invalid `#`, so the program is rejected.
        parseFails("contig.sol", "func f(): Unit {\n    val s = r #\"abc\"#\n}\n");
    }
}
