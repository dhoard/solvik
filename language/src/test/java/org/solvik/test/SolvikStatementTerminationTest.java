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
import static org.solvik.test.SolvikTestSupport.body;
import static org.solvik.test.SolvikTestSupport.local;
import static org.solvik.test.SolvikTestSupport.onlyFunction;
import static org.solvik.test.SolvikTestSupport.parseFails;
import static org.solvik.test.SolvikTestSupport.parseOk;
import static org.solvik.test.SolvikTestSupport.ret;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.statement.ReturnStmtNode;

/**
 * Parser/AST-level statement-termination tests. A newline-terminated program and its
 * explicit-`;`-separated twin must parse to the same tree shape; because a separator sits outside
 * the statement it follows, statement spans in either form end at the last real token, which the
 * first test pins with exact source slices. Negative cases pin boundaries where a line cannot end
 * (dangling operators, concatenated statements, unclosed delimiters).
 */
public final class SolvikStatementTerminationTest {

    /** Parses both spellings and asserts identical tree shapes, returning the newline-form tree. */
    private static FunctionDeclNode parseEquivalent(String name, String newlineText, String explicitText) {
        CompilationUnitNode viaNewline = parseOk(name, newlineText);
        CompilationUnitNode viaExplicit = parseOk(name, explicitText);
        assertThat(viaNewline.shapeTree()).as("newline and semicolon forms must agree on tree shape").isEqualTo(//
                viaExplicit.shapeTree());
        return onlyFunction(viaNewline);
    }

    private static String slice(String source, org.solvik.ast.AstNode node) {
        return source.substring(node.span().startOffset(), node.span().endOffset());
    }

    /** LANGUAGE_SPEC.md section 16 headline example inside a function body. */
    @Test
    public void newlineLocalsMatchSemicolonLocals() {
        String newlines = "func f(): Integer {\n    val x = 1\n    val y = 2\n    return x\n}\n";
        String explicit = "func f(): Integer {\n    val x = 1; val y = 2; return x\n}\n";
        FunctionDeclNode fn = parseEquivalent("asi1.sol", newlines, explicit);
        assertThat(local(fn, 0).name()).isEqualTo("x");
        assertThat(local(fn, 1).name()).isEqualTo("y");
        // Spans stop at the literal: neither the newline nor a `;` is included.
        assertThat(slice(newlines, local(fn, 0))).isEqualTo("val x = 1");
        assertThat(slice(newlines, local(fn, 1))).isEqualTo("val y = 2");
        assertThat(slice(newlines, ret(fn, 2))).isEqualTo("return x");
    }

    /** Multiline expressions after operators survive intact. */
    @Test
    public void multilineExpressionAfterOperatorsIsOneStatement() {
        String newlines = "func f(price: Integer, tax: Integer, shipping: Integer): Integer {\n    val total = price +\n        tax +\n        shipping\n    return total\n}\n";
        String explicit = "func f(price: Integer, tax: Integer, shipping: Integer): Integer {\n    val total = price + tax + shipping; return total\n}\n";
        FunctionDeclNode fn = parseEquivalent("asi2.sol", newlines, explicit);
        assertThat(body(fn).statements().size()).isEqualTo(2);
        assertThat(slice(newlines, local(fn, 0))).isEqualTo("val total = price +\n        tax +\n        shipping");
    }

    /** `return` followed by a newline terminates the return; the next line is a new statement. */
    @Test
    public void returnNewlineTerminatesTheReturn() {
        String newlines = "func f(value: Integer): Unit {\n    return\n    value\n}\n";
        String explicit = "func f(value: Integer): Unit {\n    return; value\n}\n";
        FunctionDeclNode fn = parseEquivalent("asi3.sol", newlines, explicit);
        List<?> statements = body(fn).statements();
        assertThat(statements.size()).isEqualTo(2);
        ReturnStmtNode first = (ReturnStmtNode) statements.get(0);
        assertThat(first.value().isEmpty()).as("the return carries no value").isTrue();
        assertThat(slice(newlines, first)).isEqualTo("return");
        assertThat(slice(newlines, (org.solvik.ast.AstNode) statements.get(1))).isEqualTo("value");
    }

    /** Leading-dot chains fold into one member-chain expression. */
    @Test
    public void leadingDotChainParsesAsSingleExpression() {
        String newlines = "func f(service: Service): Result {\n    val result = service\n        .load()\n        .transform()\n    return result\n}\n";
        String explicit = "func f(service: Service): Result {\n    val result = service.load().transform(); return result\n}\n";
        FunctionDeclNode fn = parseEquivalent("asi4.sol", newlines, explicit);
        CallExprNode outer = (CallExprNode) local(fn, 0).initializer();
        MemberAccessExprNode chain = (MemberAccessExprNode) outer.callee();
        assertThat(chain.memberName()).isEqualTo("transform");
        assertThat(slice(newlines, local(fn, 0).initializer())).isEqualTo("service\n        .load()\n        .transform()");
    }

    /** `}` followed by `else` on the next line: the else stays attached to the if. */
    @Test
    public void danglingElseOnNextLineStaysAttached() {
        String newlines = "func f(c: Boolean): Unit {\n    if (c) {\n        g()\n    }\n    else {\n        h()\n    }\n}\n";
        String explicit = "func f(c: Boolean): Unit {\n    if (c) {\n        g()\n    }\n    else {\n        h()\n    }\n}\n";
        FunctionDeclNode fn = parseEquivalent("asi5.sol", newlines, explicit);
        assertThat(body(fn).statements().size()).isEqualTo(1);
    }

    /** Blank lines and comment-only lines between statements change nothing structural. */
    @Test
    public void blankAndCommentLinesDoNotChangeTheTree() {
        String spaced = "func f(): Integer {\n\n    val x = 1\n\n    // a note\n\n    val y = 2\n\n    /* block\n       note */\n\n    return x\n\n}\n";
        String tight = "func f(): Integer {\n    val x = 1\n    val y = 2\n    return x\n}\n";
        FunctionDeclNode fn = parseEquivalent("asi6.sol", spaced, tight);
        assertThat(body(fn).statements().size()).isEqualTo(3);
        assertThat(slice(spaced, local(fn, 0))).isEqualTo("val x = 1");
        assertThat(slice(spaced, local(fn, 1))).isEqualTo("val y = 2");
        assertThat(slice(spaced, ret(fn, 2))).isEqualTo("return x");
    }

    /** Call statements terminated by a newline, including at end of file with no trailing newline. */
    @Test
    public void expressionStatementsTerminateOnNewlineAndEof() {
        String src = "func f(): Unit {\n    g(1)\n    obj.store(2)\n}";
        FunctionDeclNode fn = parseEquivalent("asi7.sol", src, "func f(): Unit {\n    g(1); obj.store(2)\n}");
        assertThat(body(fn).statements().size()).isEqualTo(2);
        assertThat(slice(src, body(fn).statements().get(1))).isEqualTo("obj.store(2)");
    }

    /** Mixed same-line separation and newline termination compose without empty statements. */
    @Test
    public void mixedTerminationProducesNoEmptyStatements() {
        String src = "func f(): Integer {\n    val x = 1; val y = 2\n    val z = 3\n    return x\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("asi8.sol", src));
        assertThat(body(fn).statements().size()).as("standalone semis produce no AST statements").isEqualTo(4);
        // The separators sit outside the statement: every form of termination leaves the span on
        // the code.
        assertThat(slice(src, local(fn, 2))).as("separators never belong to the statement span").isEqualTo("val z = 3");
    }

    /** Carriage-return line endings terminate identically to LF and keep valid spans. */
    @Test
    public void crlfSourceMatchesLfSource() {
        String lf = "func f(): Integer {\n    val x = 1\n    return x\n}\n";
        String crlf = lf.replace("\n", "\r\n");
        FunctionDeclNode fromLf = onlyFunction(parseOk("asi9.sol", lf));
        CompilationUnitNode fromCrlf = parseOk("asi9.sol", crlf);
        assertThat(onlyFunction(fromCrlf).shapeTree()).isEqualTo(fromLf.shapeTree());
        assertThat(slice(crlf, local(onlyFunction(fromCrlf), 0))).isEqualTo("val x = 1");
        assertThat(slice(crlf, ret(onlyFunction(fromCrlf), 1))).isEqualTo("return x");
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
        parseFails("bad3.sol", "func f(a: Integer): Integer {\n    return a +\n}\n");
        parseFails("bad4.sol", "func f(): Unit {\n    val x =\n    val y = 1\n}\n");
    }

    /** An unclosed `(` has no line that may end, so its statement never terminates. */
    @Test
    public void unclosedParenthesisNeverTerminatesAtEndOfFile() {
        parseFails("bad5.sol", "func f(): Integer {\n    val x = (1\n}\n");
    }

    /** `?.` chains stay one statement and are accepted as safe member access from Phase 10. */
    @Test
    public void nullableChainStaysOneStatementAndParses() {
        String src = "func f(service: Service): Result {\n    val result = service\n        ?.load()\n    return result\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("nullable.sol", src));
        assertThat(body(fn).statements().size()).isEqualTo(2);
        CallExprNode call = (CallExprNode) local(fn, 0).initializer();
        MemberAccessExprNode member = (MemberAccessExprNode) call.callee();
        assertThat(member.isSafe()).as("the safe access must be marked safe").isTrue();
        assertThat(member.memberName()).isEqualTo("load");
    }

    /** Bracket tokens exist for depth tracking only; bracket syntax is still rejected. */
    @Test
    public void bracketSyntaxIsLexedButStillRejected() {
        parseFails("brackets.sol", "func f(): Integer {\n    val x = g[1]\n    return x\n}\n");
    }

    /** Programs with no explicit semicolons at all parse purely through physical-line termination. */
    @Test
    public void programsWithoutAnyExplicitSemicolonParse() {
        String src = "func add(a: Integer, b: Integer): Integer {\n    val sum = a + b\n    return sum\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("asi10.sol", src));
        assertThat(src.chars().filter(c -> c == ';').count()).isEqualTo(0);
        assertThat(body(fn).statements().size()).isEqualTo(2);
        assertThat(slice(src, local(fn, 0))).isEqualTo("val sum = a + b");
    }

    /** Empty and whitespace-only inputs remain valid units under newline termination. */
    @Test
    public void whitespaceOnlySourcesStillParse() {
        assertThat(parseOk("asi11.sol", "").declarations()).isEqualTo(List.of());
        assertThat(parseOk("asi12.sol", "\n\n// only a comment\n").declarations()).isEqualTo(List.of());
    }

    /** A `;` that separates nothing on its line is rejected, at the semicolon, with the named code. */
    @Test
    public void standaloneSemisAreRejected() {
        assertThat(parseFails("asi13.sol", ";\nfunc a(): Unit {\n}\n").all().get(0).code())
                .isEqualTo(org.solvik.diagnostic.DiagnosticCode.PARSER_SEMI_ENDS_LINE);
        assertThat(parseFails("asi14.sol", "func a(): Unit {\n    g(1)\n    ;\n}\n").all().get(0).code())
                .isEqualTo(org.solvik.diagnostic.DiagnosticCode.PARSER_SEMI_ENDS_LINE);
    }

    /**
     * LANGUAGE_SPEC.md section 16: `;` separates two constructs on one physical line and never
     * terminates one. Every rejection arm removes what the semicolon should have separated while
     * the rest of the program stays legal; each is reported at the semicolon itself.
     */
    @Test
    public void semicolonSeparatesAndNeverTerminates() {
        // Valid: the specification's own same-line spellings.
        parseOk("semi1.sol", "val a = 1; val b = 2\n");
        parseOk("semi2.sol", "func f(): Unit {\n    foo(); bar(); baz()\n}\n");
        parseOk("semi3.sol", "func f(): Integer {\n    val v = {\n        1; 42\n    }\n    return v\n}\n");
        // Invalid: the semicolon ends its line, with and without a trailing comment.
        assertThat(parseFails("semi4.sol", "val x = 1;\n").all().get(0).code())
                .isEqualTo(org.solvik.diagnostic.DiagnosticCode.PARSER_SEMI_ENDS_LINE);
        assertThat(parseFails("semi5.sol", "println(\"one\"); // comment\n").all().get(0).code())
                .isEqualTo(org.solvik.diagnostic.DiagnosticCode.PARSER_SEMI_ENDS_LINE);
        // Invalid: a run of same-line statements whose last `;` terminates instead of separates.
        assertThat(parseFails("semi6.sol", "func f(): Unit {\n    foo(); bar();\n}\n").all().get(0).code())
                .isEqualTo(org.solvik.diagnostic.DiagnosticCode.PARSER_SEMI_ENDS_LINE);
        // Invalid: the `;` before the stand-alone closing brace of the enclosing block.
        assertThat(parseFails("semi7.sol", "func f(): Integer {\n    val v = {\n        42;\n    }\n    return v\n}\n").all().get(0).code())
                .isEqualTo(org.solvik.diagnostic.DiagnosticCode.PARSER_SEMI_ENDS_LINE);
        // Invalid: the `;` that ends the file.
        assertThat(parseFails("semi8.sol", "print(1);").all().get(0).code())
                .isEqualTo(org.solvik.diagnostic.DiagnosticCode.PARSER_SEMI_ENDS_LINE);
        // The diagnostic points at the semicolon itself.
        org.solvik.diagnostic.Diagnostic first = parseFails("semi9.sol", "val x = 1;\nprint(x)\n").all().get(0);
        assertThat(first.code()).isEqualTo(org.solvik.diagnostic.DiagnosticCode.PARSER_SEMI_ENDS_LINE);
        assertThat(first.message()).contains(";");
    }
}
