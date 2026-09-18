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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.solvik.test.SolvikTestSupport.assertNode;
import static org.solvik.test.SolvikTestSupport.assertSpan;
import static org.solvik.test.SolvikTestSupport.binary;
import static org.solvik.test.SolvikTestSupport.call;
import static org.solvik.test.SolvikTestSupport.expr;
import static org.solvik.test.SolvikTestSupport.local;
import static org.solvik.test.SolvikTestSupport.member;
import static org.solvik.test.SolvikTestSupport.name;
import static org.solvik.test.SolvikTestSupport.onlyFunction;
import static org.solvik.test.SolvikTestSupport.paren;
import static org.solvik.test.SolvikTestSupport.parseOk;
import static org.solvik.test.SolvikTestSupport.ret;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.AstNode;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.ast.expression.BinaryOperator;
import org.solvik.ast.expression.BoolLiteralNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.IntLiteralNode;
import org.solvik.ast.expression.MapEntryExprNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.ParenExprNode;
import org.solvik.ast.expression.StringLiteralNode;
import org.solvik.ast.statement.BindingKind;
import org.solvik.ast.statement.BlockNode;
import org.solvik.ast.statement.ElseBranchNode;
import org.solvik.ast.statement.IfStmtNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.source.SourceSpan;

/**
 * Positive parser tests: every supported construct with exact tree shape and exact source spans.
 * Inputs use explicit {@code ;} termination; newline termination and the newline/semicolon
 * equivalence properties are covered by {@link SolvikSemicolonInsertionTest} and
 * {@link SolvikSemicolonTokenStreamTest}.
 */
public final class SolvikParserTest {

    private static final String DOC = "func add(a: Int, b: Int): Int {\n    return a + b;\n}\n";

    @Test
    public void docExampleShapeAndSpans() {
        CompilationUnitNode cu = parseOk("doc.sol", DOC);
        assertThat(cu.declarations().size()).isEqualTo(1);
        // The compilation unit covers the whole text except the trailing newline.
        assertSpan(cu, AstKind.COMPILATION_UNIT, 0, DOC.length() - 1);

        FunctionDeclNode fn = onlyFunction(cu);
        assertNode(fn, AstKind.FUNCTION_DECL, DOC, DOC.substring(0, DOC.indexOf('}') + 1));
        assertThat(fn.name()).isEqualTo("add");
        assertThat(fn.returnType().name()).isEqualTo("Int");

        int aPos = DOC.indexOf("a: Int");
        assertNode(fn.parameters().get(0), AstKind.PARAMETER, DOC, "a: Int");
        assertNode(fn.parameters().get(1), AstKind.PARAMETER, DOC, "b: Int");
        assertSpan(fn.parameters().get(0).type(), AstKind.TYPE_REF, aPos + 3, aPos + 6);
        assertThat(fn.parameters().get(0).type().name()).isEqualTo("Int");
        assertThat(fn.parameters().get(1).type().name()).isEqualTo("Int");

        BlockNode body = fn.body();
        assertNode(body, AstKind.BLOCK, DOC, DOC.substring(DOC.indexOf('{'), DOC.indexOf('}') + 1));
        assertThat(body.statements().size()).isEqualTo(1);

        ReturnStmtNode stmt = ret(fn, 0);
        assertNode(stmt, AstKind.RETURN_STMT, DOC, "return a + b;");
        assertThat(stmt.value().isPresent()).isTrue();

        BinaryExprNode bin = binary(stmt.value().get());
        assertNode(bin, AstKind.BINARY_EXPR, DOC, "a + b");
        assertThat(bin.operator()).isEqualTo(BinaryOperator.ADD);
        assertNode(bin.left(), AstKind.NAME_REF_EXPR, DOC, "a");
        assertNode(bin.right(), AstKind.NAME_REF_EXPR, DOC, "b");
        assertThat(name(bin.left()).name()).isEqualTo("a");
        assertThat(name(bin.right()).name()).isEqualTo("b");
    }

    /** A {@code key: value} argument parses as a map entry with the pair's own span. */
    @Test
    public void mapConstructionParsesKeyValueEntries() {
        String source = "func f() {\n    Map<Int, String>(1: \"one\", 2: \"two\");\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("map.sol", source));
        CallExprNode construction = call(expr(fn, 0).expression());
        assertThat(construction.typeArguments().size()).isEqualTo(2);
        assertThat(construction.typeArguments().get(0).name()).isEqualTo("Int");
        assertThat(construction.typeArguments().get(1).name()).isEqualTo("String");
        assertThat(construction.arguments().size()).isEqualTo(2);
        MapEntryExprNode first = (MapEntryExprNode) construction.arguments().get(0);
        assertNode(first, AstKind.MAP_ENTRY_EXPR, source, "1: \"one\"");
        assertNode(first.key(), AstKind.INT_LITERAL, source, "1");
        assertNode(first.value(), AstKind.STRING_LITERAL, source, "\"one\"");
        MapEntryExprNode second = (MapEntryExprNode) construction.arguments().get(1);
        assertNode(second, AstKind.MAP_ENTRY_EXPR, source, "2: \"two\"");
    }

    /** A call argument list may end with a trailing comma; it contributes no argument. */
    @Test
    public void trailingCommaInCallArgumentsIsAccepted() {
        String src = "func f(): Unit {\n    g(1, 2,);\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("trailingarg.sol", src));
        CallExprNode c = call(expr(fn, 0).expression());
        assertThat(c.arguments().size()).isEqualTo(2);
        assertNode(c, AstKind.CALL_EXPR, src, "g(1, 2,)");
        assertNode(c.arguments().get(0), AstKind.INT_LITERAL, src, "1");
        assertNode(c.arguments().get(1), AstKind.INT_LITERAL, src, "2");
    }

    /** A {@code Map} construction is an argument list, so it accepts a trailing comma too. */
    @Test
    public void trailingCommaInMapEntriesIsAccepted() {
        String src = "func f() {\n    Map<Int, String>(1: \"one\", 2: \"two\",);\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("maptrailing.sol", src));
        CallExprNode construction = call(expr(fn, 0).expression());
        assertThat(construction.arguments().size()).isEqualTo(2);
        MapEntryExprNode second = (MapEntryExprNode) construction.arguments().get(1);
        assertNode(second, AstKind.MAP_ENTRY_EXPR, src, "2: \"two\"");
    }

    /** A trailing comma before a closing delimiter on the next line parses across the boundary. */
    @Test
    public void multilineArgumentListWithTrailingCommaIsAccepted() {
        String src = "func f(): Unit {\n    g(\n        1,\n        2,\n    );\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("multilinearg.sol", src));
        CallExprNode c = call(expr(fn, 0).expression());
        assertThat(c.arguments().size()).isEqualTo(2);
        assertNode(c, AstKind.CALL_EXPR, src, "g(\n        1,\n        2,\n    )");
    }

    /** A call with explicit type arguments accepts a trailing comma before its {@code )}. */
    @Test
    public void trailingCommaAfterExplicitTypeArgumentsIsAccepted() {
        String src = "func f(): Unit {\n    List<Int>(1, 2, 3,);\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("trailingtypearg.sol", src));
        CallExprNode c = call(expr(fn, 0).expression());
        assertThat(c.typeArguments().size()).isEqualTo(1);
        assertThat(c.typeArguments().get(0).name()).isEqualTo("Int");
        assertThat(c.arguments().size()).isEqualTo(3);
    }

    /** A single argument with a trailing comma is still a one-argument call. */
    @Test
    public void singleArgumentWithTrailingCommaIsAccepted() {
        String src = "func f(): Unit {\n    g(1,);\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("singlearg.sol", src));
        CallExprNode c = call(expr(fn, 0).expression());
        assertThat(c.arguments().size()).isEqualTo(1);
        assertNode(c.arguments().get(0), AstKind.INT_LITERAL, src, "1");
    }

    /** A callable may omit its return type; the omitted type is `Unit` (specification section 6). */
    @Test
    public void functionMayOmitItsReturnType() {
        String src = "func greet() {\n    return;\n}\n";
        CompilationUnitNode cu = parseOk("greet.sol", src);
        FunctionDeclNode fn = onlyFunction(cu);
        assertThat(fn.name()).isEqualTo("greet");
        assertNode(fn, AstKind.FUNCTION_DECL, src, src.substring(0, src.indexOf('}') + 1));
        assertThat(fn.returnType().name()).isEqualTo("Unit");
        assertThat(fn.body().statements().size()).isEqualTo(1);
    }

    @Test
    public void docExampleExactShapeTree() {
        CompilationUnitNode cu = parseOk("doc.sol", DOC);
        assertThat(cu.shapeTree()).isEqualTo("""
                COMPILATION_UNIT {
                  FUNCTION_DECL {
                    PARAMETER {
                      TYPE_REF
                    }
                    PARAMETER {
                      TYPE_REF
                    }
                    TYPE_REF
                    BLOCK {
                      RETURN_STMT {
                        BINARY_EXPR {
                          NAME_REF_EXPR
                          NAME_REF_EXPR
                        }
                      }
                    }
                  }
                }
                """);
    }

    @Test
    public void literalsNamesAndStrings() {
        String src = "func f(): Unit {\n" + //
                "  val a: Int = 42;\n" + //
                "  var b: Boolean = true;\n" + //
                "  val c = \"esc\\t\";\n" + //
                "}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("literals.sol", src));

        LocalDeclNode a = local(fn, 0);
        assertNode(a, AstKind.LOCAL_DECL, src, "val a: Int = 42;");
        assertThat(a.bindingKind()).isEqualTo(BindingKind.VAL);
        assertThat(a.name()).isEqualTo("a");
        assertThat(a.declaredType().orElseThrow().name()).isEqualTo("Int");
        IntLiteralNode lit = (IntLiteralNode) a.initializer();
        assertNode(lit, AstKind.INT_LITERAL, src, "42");
        assertThat(lit.lexeme()).isEqualTo("42");

        LocalDeclNode b = local(fn, 1);
        assertNode(b, AstKind.LOCAL_DECL, src, "var b: Boolean = true;");
        assertThat(b.bindingKind()).isEqualTo(BindingKind.VAR);
        BoolLiteralNode bool = (BoolLiteralNode) b.initializer();
        assertNode(bool, AstKind.BOOL_LITERAL, src, "true");
        assertThat(bool.value()).isTrue();

        LocalDeclNode c = local(fn, 2);
        assertNode(c, AstKind.LOCAL_DECL, src, "val c = \"esc\\t\";");
        assertThat(c.declaredType().isEmpty()).isTrue();
        StringLiteralNode str = (StringLiteralNode) c.initializer();
        assertNode(str, AstKind.STRING_LITERAL, src, "\"esc\\t\"");
        assertThat(str.lexeme()).isEqualTo("\"esc\\t\"");
    }

    @Test
    public void falseLiteralParsesAsBooleanFalse() {
        String src = "func f(): Boolean {\n    return false;\n}\n";
        ReturnStmtNode r = ret(onlyFunction(parseOk("bool.sol", src)), 0);
        BoolLiteralNode bool = (BoolLiteralNode) r.value().get();
        assertThat(bool.kind()).isEqualTo(AstKind.BOOL_LITERAL);
        assertThat(bool.value()).isFalse();
    }

    @Test
    public void binaryPrecedenceAssociativityAndParentheses() {
        String src = "func f(): Int {\n    val x = 1 - 2 - 3 * 4;\n    val y = (1 + 2) / 3;\n    return x;\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("prec.sol", src));

        BinaryExprNode top = binary(local(fn, 0).initializer());
        // Left associativity groups as ((1 - 2) - (3 * 4)).
        assertThat(top.operator()).isEqualTo(BinaryOperator.SUB);
        BinaryExprNode left = binary(top.left());
        assertThat(left.operator()).isEqualTo(BinaryOperator.SUB);
        assertThat(left.left().kind()).isEqualTo(AstKind.INT_LITERAL);
        BinaryExprNode mul = binary(top.right());
        assertThat(mul.operator()).isEqualTo(BinaryOperator.MUL);
        assertNode(top, AstKind.BINARY_EXPR, src, "1 - 2 - 3 * 4");
        assertNode(left, AstKind.BINARY_EXPR, src, "1 - 2");
        assertNode(mul, AstKind.BINARY_EXPR, src, "3 * 4");

        BinaryExprNode div = binary(local(fn, 1).initializer());
        assertThat(div.operator()).isEqualTo(BinaryOperator.DIV);
        ParenExprNode p = paren(div.left());
        assertNode(p, AstKind.PAREN_EXPR, src, "(1 + 2)");
        assertNode(p.inner(), AstKind.BINARY_EXPR, src, "1 + 2");
    }

    @Test
    public void multiplicationBindsTighterAcrossMixedOperators() {
        String src = "func f(a: Int, b: Int, c: Int): Int {\n    return a + b * c - a;\n}\n";
        ReturnStmtNode r = ret(onlyFunction(parseOk("mix.sol", src)), 0);
        BinaryExprNode top = binary(r.value().get());
        assertThat(top.operator()).isEqualTo(BinaryOperator.SUB);
        BinaryExprNode add = binary(top.left());
        assertThat(add.operator()).isEqualTo(BinaryOperator.ADD);
        assertThat(binary(add.right()).operator()).isEqualTo(BinaryOperator.MUL);
        assertNode(add, AstKind.BINARY_EXPR, src, "a + b * c");
        assertNode(binary(add.right()), AstKind.BINARY_EXPR, src, "b * c");
    }

    @Test
    public void callsMemberAccessAndFoldingOrder() {
        String src = "func f(): Unit {\n    obj.method(1)(2);\n    g(h.a.b(3), p + q.r);\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("calls.sol", src));

        CallExprNode outer = call(expr(fn, 0).expression());
        assertThat(outer.arguments().size()).isEqualTo(1);
        CallExprNode inner = call(outer.callee());
        MemberAccessExprNode m = member(inner.callee());
        assertThat(m.memberName()).isEqualTo("method");
        assertThat(name(m.receiver()).name()).isEqualTo("obj");
        // Folded nodes start at the base primary and end at their own suffix.
        assertNode(m, AstKind.MEMBER_ACCESS_EXPR, src, "obj.method");
        assertNode(inner, AstKind.CALL_EXPR, src, "obj.method(1)");
        assertNode(outer, AstKind.CALL_EXPR, src, "obj.method(1)(2)");
        assertThat(inner.arguments().size()).isEqualTo(1);

        CallExprNode call2 = call(expr(fn, 1).expression());
        assertThat(name(call2.callee()).name()).isEqualTo("g");
        assertThat(call2.arguments().size()).isEqualTo(2);
        CallExprNode hab = call(call2.arguments().get(0));
        assertNode(hab, AstKind.CALL_EXPR, src, "h.a.b(3)");
        assertNode(member(hab.callee()), AstKind.MEMBER_ACCESS_EXPR, src, "h.a.b");
        assertThat(hab.arguments().size()).isEqualTo(1);
        BinaryExprNode plus = binary(call2.arguments().get(1));
        assertThat(plus.operator()).isEqualTo(BinaryOperator.ADD);
        assertNode(member(plus.right()), AstKind.MEMBER_ACCESS_EXPR, src, "q.r");
    }

    @Test
    public void callOnParenthesizedExpression() {
        String src = "func f(a: Int, b: Int, c: Int): Unit {\n    (a + b).c(1);\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("parencall.sol", src));
        CallExprNode c = call(expr(fn, 0).expression());
        MemberAccessExprNode m = member(c.callee());
        ParenExprNode p = paren(m.receiver());
        assertNode(p, AstKind.PAREN_EXPR, src, "(a + b)");
        assertThat(m.memberName()).isEqualTo("c");
        assertNode(c, AstKind.CALL_EXPR, src, "(a + b).c(1)");
    }

    @Test
    public void zeroArgumentCallsAreDistinctFromMemberReads() {
        String src = "func f(): Unit {\n    obj.load();\n    obj.field;\n    plain();\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("noargs.sol", src));
        assertNode(expr(fn, 0).expression(), AstKind.CALL_EXPR, src, "obj.load()");
        assertNode(expr(fn, 1).expression(), AstKind.MEMBER_ACCESS_EXPR, src, "obj.field");
        assertNode(expr(fn, 2).expression(), AstKind.CALL_EXPR, src, "plain()");
        assertThat(call(expr(fn, 2).expression()).arguments().size()).isEqualTo(0);
    }

    @Test
    public void nestedParenthesesPreserveDepth() {
        String src = "func f(a: Int): Int {\n    return ((a));\n}\n";
        ReturnStmtNode r = ret(onlyFunction(parseOk("nestparen.sol", src)), 0);
        ParenExprNode outer = paren(r.value().get());
        assertNode(outer, AstKind.PAREN_EXPR, src, "((a))");
        assertNode(paren(outer.inner()), AstKind.PAREN_EXPR, src, "(a)");
        assertNode(paren(outer.inner()).inner(), AstKind.NAME_REF_EXPR, src, "a");
    }

    @Test
    public void ifElseIfElseStructure() {
        String src = "func f(c: Boolean): Unit {\n" + //
                "    if (c) {\n        return;\n    } else if (c) {\n        return;\n    } else {\n        return;\n    }\n" + //
                "}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("if.sol", src));
        IfStmtNode if1 = (IfStmtNode) fn.body().statements().get(0);
        assertNode(if1.condition(), AstKind.NAME_REF_EXPR, src, "c");
        ElseBranchNode else1 = if1.elseBranch().orElseThrow();
        assertThat(else1.isChainedIf()).isTrue();
        assertNode(else1, AstKind.ELSE_BRANCH, src, "else if (c) {\n        return;\n    } else {\n        return;\n    }");
        IfStmtNode if2 = else1.chainedIf().orElseThrow();
        ElseBranchNode else2 = if2.elseBranch().orElseThrow();
        assertThat(else2.isChainedIf()).isFalse();
        assertNode(else2.block().orElseThrow(), AstKind.BLOCK, src, "{\n        return;\n    }");
        assertNode(if1.thenBlock(), AstKind.BLOCK, src, "{\n        return;\n    }");
    }

    @Test
    public void ifWithoutElseHasNoBranch() {
        String src = "func f(c: Boolean): Unit {\n    if (c) {\n        g();\n    }\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("if.sol", src));
        IfStmtNode if1 = (IfStmtNode) fn.body().statements().get(0);
        assertThat(if1.elseBranch().isEmpty()).isTrue();
        List<AstNode> kids = if1.children();
        assertThat(kids.size()).isEqualTo(2);
        assertThat(kids.get(1).kind()).isEqualTo(AstKind.BLOCK);
    }

    @Test
    public void blocksMayBeEmpty() {
        String src = "func f(): Unit {\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("emptybody.sol", src));
        assertThat(fn.body().statements().size()).isEqualTo(0);
        assertThat(fn.parameters().size()).isEqualTo(0);
        assertNode(fn.body(), AstKind.BLOCK, src, "{\n}");
    }

    @Test
    public void bareReturnHasNoValue() {
        String src = "func f(): Unit {\n    return;\n}\n";
        ReturnStmtNode r = ret(onlyFunction(parseOk("bare.sol", src)), 0);
        assertThat(r.value().isEmpty()).isTrue();
        assertThat(r.children()).isEqualTo(List.of());
        assertNode(r, AstKind.RETURN_STMT, src, "return;");
    }

    @Test
    public void multipleFunctionsPreserveOrderAndSpans() {
        String src = "func a(): Unit {\n}\nfunc b(x: Int): Int {\n    return x;\n}\n";
        CompilationUnitNode cu = parseOk("two.sol", src);
        assertThat(cu.declarations().size()).isEqualTo(2);
        assertThat(((FunctionDeclNode) cu.declarations().get(0)).name()).isEqualTo("a");
        assertThat(((FunctionDeclNode) cu.declarations().get(1)).name()).isEqualTo("b");
        assertSpan(cu.declarations().get(0), AstKind.FUNCTION_DECL, 0, src.indexOf('}') + 1);
        assertSpan(cu.declarations().get(1), AstKind.FUNCTION_DECL, src.indexOf("func b"), src.lastIndexOf('}') + 1);
    }

    @Test
    public void emptySourceParsesToEmptyUnit() {
        CompilationUnitNode cu = parseOk("empty.sol", "");
        assertThat(cu.declarations()).isEqualTo(List.of());
        assertThat(cu.span()).isEqualTo(SourceSpan.of(0, 0));
    }

    /** Structural invariant: every node span is contained in its parent span. */
    @Test
    public void spansAreNestedWithinParents() {
        String src = "func f(x: Int): Int {\n" + //
                "    val y: Int = (x + g(x, obj.f(1))) * 2;\n" + //
                "    obj.a.b(1).c;\n" + //
                "    if (true) {\n        return y;\n    } else {\n        return x;\n    }\n" + //
                "}\n";
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parseOk("nest.sol", src));
        while (!stack.isEmpty()) {
            AstNode node = stack.pop();
            for (AstNode child : node.children()) {
                assertThat(node.span().contains(child.span())).as(child.kind() + " " + child.span() + " outside " + node.kind() + " " + node.span()).isTrue();
                stack.push(child);
            }
        }
    }

    /** Structural invariant: children follow source order without overlap. */
    @Test
    public void siblingsFollowSourceOrder() {
        String src = "func f(): Unit {\n    val v = h(1, 2, obj.x);\n    return v;\n}\n";
        Deque<AstNode> stack = new ArrayDeque<>();
        stack.push(parseOk("order.sol", src));
        while (!stack.isEmpty()) {
            AstNode node = stack.pop();
            AstNode previous = null;
            for (AstNode child : node.children()) {
                if (previous != null) {
                    assertThat(previous.span().startOffset() <= child.span().startOffset()).as("out of order: " + previous.span() + " then " + child.span()).isTrue();
                    assertThat(!previous.span().overlaps(child.span())).as("overlap: " + previous.span() + " " + child.span()).isTrue();
                }
                previous = child;
                stack.push(child);
            }
        }
    }

    @Test
    public void childCollectionsAreUnmodifiable() {
        CompilationUnitNode cu = parseOk("imm.sol", DOC);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> cu.children().add(null));
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> cu.declarations().add(null));
        FunctionDeclNode fn = onlyFunction(cu);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> fn.parameters().clear());
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> fn.body().statements().clear());
    }

    /** Re-parsing identical sources yields structurally identical trees. */
    @Test
    public void parsingIsDeterministic() {
        String first = parseOk("d1.sol", DOC).shapeTree();
        String second = parseOk("d2.sol", DOC).shapeTree();
        assertThat(second).isEqualTo(first);
    }

    /**
     * The former keyword is now an ordinary identifier: {@code func fun(): Unit} declares a
     * function named {@code fun}. This locks in that no compatibility path accepts the old
     * spelling as a keyword.
     */
    @Test
    public void formerKeywordIsNowAnOrdinaryIdentifier() {
        FunctionDeclNode fn = onlyFunction(parseOk("former.sol", "func fun(): Unit {\n}\n"));
        assertThat(fn.name()).isEqualTo("fun");
    }
}
