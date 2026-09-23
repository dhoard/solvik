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
import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import org.junit.jupiter.api.Test;
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
        assertThat(result.isSuccess()).as("parse must fail: " + text).isFalse();
        assertThat(result.diagnostics().hasErrors()).as("failed parse must carry at least one error").isTrue();
        assertThat(result.ast().isEmpty()).as("failed parse must expose no AST").isTrue();
        try {
            result.requireAst();
            fail("requireAst() must reject failed parses");
        } catch (IllegalStateException expected) {
            // expected
        }
        for (Diagnostic d : result.diagnostics().all()) {
            assertThat(d.severity()).isEqualTo(DiagnosticSeverity.ERROR);
            assertThat(d.span().endOffset() <= text.length()).as("span within source bounds: " + d.span()).isTrue();
        }
        return result.diagnostics();
    }

    private static Diagnostic first(DiagnosticBag bag) {
        List<Diagnostic> all = bag.all();
        assertThat(all.isEmpty()).isFalse();
        return all.get(0);
    }

    @Test
    public void simpleLanguageFunctionKeywordIsRejectedAsLegacy() {
        DiagnosticBag bag = expectErrors("legacy.sol", "function foo(x) {\n  return x;\n}\n");
        Diagnostic d = first(bag);
        assertThat(d.code()).isEqualTo(DiagnosticCode.PARSER_UNSUPPORTED_LEGACY_SYNTAX);
        assertThat(d.code().stableCode()).isEqualTo("SOLV-PARS-004");
        assertThat(d.span()).isEqualTo(SourceSpan.of(0, "function".length()));
        assertThat(d.expected().orElseThrow()).isEqualTo("'func'");
        assertThat(d.found().orElseThrow()).isEqualTo("'function'");
    }

    @Test
    public void dynamicFunctionSyntaxIsRejected() {
        expectErrors("legacy2.sol", "function add(a, b) {\n  return a + b;\n}\n");
    }

    /**
     * The legacy classification is attached to any unexpected {@code function} token, not only to the
     * declaration-leading one: here the token is what the parser rejects directly, so the diagnostic
     * names it as the offending token rather than as the preceding one.
     */
    @Test
    public void anUnexpectedFunctionTokenIsStillClassifiedAsLegacySyntax() {
        String src = "func f(): Unit {\n    g(1 function);\n}\n";
        DiagnosticBag bag = expectErrors("legacy3.sol", src);
        Diagnostic d = first(bag);
        assertThat(d.code()).isEqualTo(DiagnosticCode.PARSER_UNSUPPORTED_LEGACY_SYNTAX);
        int at = src.indexOf("function");
        assertThat(d.span()).isEqualTo(SourceSpan.of(at, at + "function".length()));
        assertThat(d.found().orElseThrow()).isEqualTo("'function'");
    }

    /**
     * The former function keyword is still an ordinary identifier, so using it as a plain name is not
     * legacy syntax and parses. This protects the removal regression from becoming name-based.
     */
    @Test
    public void functionRemainsAUsableIdentifier() {
        SolvikParseResult result = org.solvik.parser.SolvikParser.parse(new SourceFile("ident.sol", "func f(): Unit {\n    val function = 1;\n    g(function);\n}\n"));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.diagnostics().all()).isEmpty();
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
        DiagnosticBag bag = expectErrors("nosemi.sol", "func f(): Integer {\n    val x: Integer = (1\n}\n");
        assertThat(first(bag).code() == DiagnosticCode.PARSER_UNEXPECTED_TOKEN || first(bag).code() == DiagnosticCode.PARSER_INCOMPLETE_INPUT).isTrue();
    }

    @Test
    public void statementsConcatenatedOnOneLineAreRejected() {
        // No newline and no ';': insertion has no boundary to act on.
        expectErrors("nosemi2.sol", "func f(): Unit {\n    g(1) h(2)\n}\n");
    }

    @Test
    public void lineEndingInOperatorIsNotTerminated() {
        // `+` is not an eligible terminator, so the expression runs into `}` and fails.
        DiagnosticBag bag = expectErrors("nosemi3.sol", "func f(a: Integer): Integer {\n    return a +\n}\n");
        assertThat(first(bag).code()).isEqualTo(DiagnosticCode.PARSER_UNEXPECTED_TOKEN);
    }

    @Test
    public void returnTypeColonWithoutATypeIsRejected() {
        // A return type is optional, but a written `:` must be followed by a type
        // (docs/LANGUAGE_SPEC.md section 6).
        expectErrors("notype.sol", "func f(): {\n    return;\n}\n");
    }

    @Test
    public void untypedParameterIsRejected() {
        expectErrors("param.sol", "func f(a) : Unit {\n    return;\n}\n");
    }

    @Test
    public void parameterWithoutNameIsRejected() {
        expectErrors("param2.sol", "func f(: Integer): Unit {\n    return;\n}\n");
    }

    @Test
    public void parameterMissingColonIsRejected() {
        expectErrors("param3.sol", "func f(a Integer): Unit {\n    return;\n}\n");
    }

    @Test
    public void assignmentIsNotAnExpression() {
        // Assignment is a statement form only; using it inside an expression is a parse error.
        expectErrors("assign.sol", "func f(x: Integer): Integer {\n    val y = (x = 1);\n    return y;\n}\n");
    }

    @Test
    public void unterminatedBlockIsReportedAsIncompleteInput() {
        DiagnosticBag bag = expectErrors("eof.sol", "func f(): Unit {\n    return;\n");
        assertThat(first(bag).code()).isEqualTo(DiagnosticCode.PARSER_INCOMPLETE_INPUT);
    }

    @Test
    public void truncatedReturnAtEofIsIncomplete() {
        DiagnosticBag bag = expectErrors("eof3.sol", "func f(): Integer {\n    return ");
        assertThat(first(bag).code()).isEqualTo(DiagnosticCode.PARSER_INCOMPLETE_INPUT);
    }

    @Test
    public void unclosedCallIsRejected() {
        expectErrors("eof4.sol", "func f(): Unit {\n    g(1;\n}\n");
    }

    /**
     * A trailing comma is permitted only after an argument, so a list that has no argument at all
     * remains a parse error rather than an empty argument list with a stray comma.
     */
    @Test
    public void trailingCommaWithoutAnArgumentIsRejected() {
        expectErrors("trailcommaempty.sol", "func f(): Unit {\n    g(,);\n}\n");
    }

    @Test
    public void doubledCommaBetweenArgumentsIsRejected() {
        expectErrors("doublecomma.sol", "func f(): Unit {\n    g(1,, 2);\n}\n");
    }

    /** A trailing comma is a call-argument-list feature and is not accepted by other lists. */
    @Test
    public void otherCommaSeparatedListsStillRejectTrailingCommas() {
        expectErrors("paramcomma.sol", "func f(a: Integer,): Unit {\n    return;\n}\n");
        expectErrors("typeargcomma.sol", "func f(): Unit {\n    val xs: List<Integer,> = List<Integer>();\n}\n");
    }

    @Test
    public void invalidCharacterProducesLexerError() {
        String src = "func f(): Unit {\n    @\n}\n";
        DiagnosticBag bag = expectErrors("lex.sol", src);
        Diagnostic d = first(bag);
        assertThat(d.code()).isEqualTo(DiagnosticCode.LEXER_ERROR);
        assertThat(d.code().stableCode()).isEqualTo("SOLV-LEX-001");
        int at = src.indexOf('@');
        assertThat(d.span()).isEqualTo(SourceSpan.of(at, at + 1));
    }

    @Test
    public void unterminatedStringIsRejected() {
        String src = "func f(): Unit {\n    val s = \"oops;\n}\n";
        DiagnosticBag bag = expectErrors("str.sol", src);
        for (Diagnostic d : bag.all()) {
            assertThat(//
                    d.code() == DiagnosticCode.LEXER_ERROR || d.code().stableCode().startsWith("SOLV-PARS")).as("expected lexical or parser diagnostic, got " + d.code()).isTrue();
        }
    }

    @Test
    public void keywordUsedAsParameterNameIsRejected() {
        expectErrors("kw.sol", "func f(if: Integer): Unit {\n    return;\n}\n");
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
            assertThat(r.isSuccess()).as("must fail: [" + src + "]").isFalse();
            assertThat(r.ast().isEmpty()).as("no ast on failure: [" + src + "]").isTrue();
            assertThat(r.diagnostics().hasErrors()).as("errors present: [" + src + "]").isTrue();
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
        String ok = "func f(a: Integer): Integer {\n    val t: Integer = a * 2;\n    if (true) {\n        g(t);\n    } else {\n        h(t, obj.f);\n    }\n    return t;\n}\n";
        SolvikParseResult r = org.solvik.parser.SolvikParser.parse(new SourceFile("ok.sol", ok));
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.diagnostics().isEmpty()).isTrue();
    }
}
