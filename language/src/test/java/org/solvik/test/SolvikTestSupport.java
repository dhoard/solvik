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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.fail;

import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.ParameterNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.ast.expression.BinaryOperator;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.NameRefExprNode;
import org.solvik.ast.expression.ParenExprNode;
import org.solvik.ast.statement.BlockNode;
import org.solvik.ast.statement.ExprStmtNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.parser.SolvikParseResult;
import org.solvik.source.SourceFile;
import org.solvik.source.SourceSpan;

/** Shared helpers for Solvik Phase 1 parser tests. */
final class SolvikTestSupport {

    private SolvikTestSupport() {
    }

    static CompilationUnitNode parseOk(String name, String text) {
        SolvikParseResult result = org.solvik.parser.SolvikParser.parse(new SourceFile(name, text));
        if (!result.isSuccess()) {
            StringBuilder sb = new StringBuilder("unexpected parse errors:");
            result.diagnostics().all().forEach(d -> sb.append("\n  ").append(d));
            fail(sb.toString());
        }
        return result.requireAst();
    }

    static FunctionDeclNode onlyFunction(CompilationUnitNode cu) {
        assertThat(cu.declarations().size()).isEqualTo(1);
        return (FunctionDeclNode) cu.declarations().get(0);
    }

    /** Asserts node kind and that its span slice of {@code source} is exactly {@code expected}. */
    static void assertNode(AstNode node, AstKind kind, String source, String expected) {
        assertThat(node.kind()).isEqualTo(kind);
        int start = node.span().startOffset();
        int end = node.span().endOffset();
        assertThat(end <= source.length()).as("span out of bounds: " + node.span()).isTrue();
        assertThat(source.substring(start, end)).isEqualTo(expected);
    }

    static void assertSpan(AstNode node, AstKind kind, int start, int end) {
        assertThat(node.kind()).isEqualTo(kind);
        assertThat(node.span()).isEqualTo(SourceSpan.of(start, end));
    }

    /** Local declaration at a given index in a function body. */
    static LocalDeclNode local(FunctionDeclNode fn, int index) {
        return (LocalDeclNode) fn.body().statements().get(index);
    }

    static BlockNode body(FunctionDeclNode fn) {
        return fn.body();
    }

    static ReturnStmtNode ret(FunctionDeclNode fn, int index) {
        return (ReturnStmtNode) fn.body().statements().get(index);
    }

    static ExprStmtNode expr(FunctionDeclNode fn, int index) {
        return (ExprStmtNode) fn.body().statements().get(index);
    }

    static List<AstNode> kids(AstNode node) {
        return node.children();
    }

    static BinaryExprNode binary(AstNode node) {
        assertThat(node.kind()).isEqualTo(AstKind.BINARY_EXPR);
        return (BinaryExprNode) node;
    }

    static CallExprNode call(AstNode node) {
        assertThat(node.kind()).isEqualTo(AstKind.CALL_EXPR);
        return (CallExprNode) node;
    }

    static MemberAccessExprNode member(AstNode node) {
        assertThat(node.kind()).isEqualTo(AstKind.MEMBER_ACCESS_EXPR);
        return (MemberAccessExprNode) node;
    }

    static ParenExprNode paren(AstNode node) {
        assertThat(node.kind()).isEqualTo(AstKind.PAREN_EXPR);
        return (ParenExprNode) node;
    }

    static NameRefExprNode name(AstNode node) {
        assertThat(node.kind()).isEqualTo(AstKind.NAME_REF_EXPR);
        return (NameRefExprNode) node;
    }

    static ParameterNode param(FunctionDeclNode fn, int index) {
        return fn.parameters().get(index);
    }

    /** Asserts the source fails to parse and exposes diagnostics but no AST. */
    static org.solvik.diagnostic.DiagnosticBag parseFails(String name, String text) {
        org.solvik.parser.SolvikParseResult result = org.solvik.parser.SolvikParser.parse(new SourceFile(name, text));
        if (result.isSuccess()) {
            fail("parse must fail: " + text);
        }
        assertThat(result.diagnostics().hasErrors())
                .as("failed parse must carry diagnostics")
                .isTrue();
        assertThat(result.ast().isEmpty())
                .as("failed parse must expose no AST")
                .isTrue();
        return result.diagnostics();
    }

    /**
     * Asserts that {@code callable} throws an exception of exactly the requested (or a subtype) type
     * and returns it, mirroring the JUnit {@code assertThrows} contract while failing through AssertJ.
     */
    static <T extends Throwable> T expectThrows(Class<T> type, ThrowingCallable callable) {
        Throwable thrown = catchThrowable(callable);
        assertThat(thrown).as("expected %s to be thrown", type.getName()).isInstanceOf(type);
        return type.cast(thrown);
    }
}
