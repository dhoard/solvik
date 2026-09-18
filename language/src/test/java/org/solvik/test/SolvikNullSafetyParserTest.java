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
import static org.solvik.test.SolvikTestSupport.binary;
import static org.solvik.test.SolvikTestSupport.local;
import static org.solvik.test.SolvikTestSupport.member;
import static org.solvik.test.SolvikTestSupport.name;
import static org.solvik.test.SolvikTestSupport.onlyFunction;
import static org.solvik.test.SolvikTestSupport.parseOk;
import static org.solvik.test.SolvikTestSupport.ret;

import org.junit.jupiter.api.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.ParameterNode;
import org.solvik.ast.declaration.TypeRefNode;
import org.solvik.ast.expression.BinaryExprNode;
import org.solvik.ast.expression.BinaryOperator;
import org.solvik.ast.expression.CastExprNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.TypeTestExprNode;
import org.solvik.ast.statement.LocalDeclNode;

/**
 * Phase 10 parser/AST tests (docs/LANGUAGE_SPEC.md sections 5 and 18): the nullable type reference
 * {@code T?}, the {@code null} literal, safe member access {@code ?.}, null coalescing {@code ??},
 * and the type test and checked cast operators {@code is} / {@code as}, with their exact source
 * spans and operator precedence.
 */
public final class SolvikNullSafetyParserTest {

    @Test
    public void nullableParameterTypeIsRecorded() {
        String src = "func f(name: String?): Unit {\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("nullparam.sol", src));
        ParameterNode parameter = fn.parameters().get(0);
        TypeRefNode type = parameter.type();
        assertThat(type.name()).isEqualTo("String");
        assertThat(type.isNullable()).isTrue();
        assertNode(type, AstKind.TYPE_REF, src, "String?");
    }

    @Test
    public void nonNullableTypeReferenceStaysNonNullable() {
        FunctionDeclNode fn = onlyFunction(parseOk("plainparam.sol", "func f(name: String): Unit {\n}\n"));
        assertThat(fn.parameters().get(0).type().isNullable()).isFalse();
    }

    @Test
    public void nullableLocalTypeIsRecorded() {
        String src = "func f(): Unit {\n    val x: Int? = null\n}\n";
        LocalDeclNode declaration = local(onlyFunction(parseOk("nulllocal.sol", src)), 0);
        assertThat(declaration.declaredType().orElseThrow().isNullable()).isTrue();
    }

    @Test
    public void nullLiteralIsItsOwnNode() {
        String src = "func f(): Unit {\n    val x = null\n}\n";
        LocalDeclNode declaration = local(onlyFunction(parseOk("null.sol", src)), 0);
        assertNode(declaration.initializer(), AstKind.NULL_LITERAL, src, "null");
    }

    @Test
    public void nullableDotMarksTheAccessSafe() {
        String src = "func f(box: Box?): Int? {\n    return box?.value\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("safe.sol", src));
        MemberAccessExprNode access = member(ret(fn, 0).value().orElseThrow());
        assertThat(access.isSafe()).isTrue();
        assertThat(access.memberName()).isEqualTo("value");
        assertNode(access, AstKind.MEMBER_ACCESS_EXPR, src, "box?.value");
    }

    @Test
    public void ordinaryDotIsNotSafe() {
        FunctionDeclNode fn = onlyFunction(parseOk("plainmember.sol", "func f(box: Box): Int {\n    return box.value\n}\n"));
        assertThat(member(ret(fn, 0).value().orElseThrow()).isSafe()).isFalse();
    }

    @Test
    public void nullableDotChainStaysOneMemberAccess() {
        String src = "func f(a: A?): C? {\n    return a?.b?.c\n}\n";
        MemberAccessExprNode outer = member(ret(onlyFunction(parseOk("safechain.sol", src)), 0).value().orElseThrow());
        assertThat(outer.memberName()).isEqualTo("c");
        MemberAccessExprNode inner = member(outer.receiver());
        assertThat(inner.memberName()).isEqualTo("b");
        assertThat(inner.isSafe() && outer.isSafe()).isTrue();
    }

    @Test
    public void coalescingBindsLooserThanLogicalOr() {
        String src = "func f(): Int {\n    val x = a ?? b || c\n}\n";
        LocalDeclNode declaration = local(onlyFunction(parseOk("coalesce.sol", src)), 0);
        BinaryExprNode coalesce = binary(declaration.initializer());
        assertThat(coalesce.operator()).isEqualTo(BinaryOperator.COALESCE);
        assertThat(binary(coalesce.right()).operator()).isEqualTo(BinaryOperator.OR);
    }

    @Test
    public void coalescingIsLeftAssociative() {
        String src = "func f(): Int {\n    val x = a ?? b ?? c\n}\n";
        BinaryExprNode outer = binary(local(onlyFunction(parseOk("coalescechain.sol", src)), 0).initializer());
        assertThat(outer.operator()).isEqualTo(BinaryOperator.COALESCE);
        assertThat(binary(outer.left()).operator()).isEqualTo(BinaryOperator.COALESCE);
        assertThat(name(outer.right()).name()).isEqualTo("c");
    }

    @Test
    public void typeTestProducesADedicatedNode() {
        String src = "func f(v: Any): Boolean {\n    return v is Box\n}\n";
        TypeTestExprNode test = (TypeTestExprNode) ret(onlyFunction(parseOk("istest.sol", src)), 0).value().orElseThrow();
        assertThat(test.typeRef().name()).isEqualTo("Box");
        assertNode(test, AstKind.TYPE_TEST_EXPR, src, "v is Box");
    }

    @Test
    public void checkedCastProducesADedicatedNode() {
        String src = "func f(v: Any): Box {\n    return v as Box\n}\n";
        CastExprNode cast = (CastExprNode) ret(onlyFunction(parseOk("cast.sol", src)), 0).value().orElseThrow();
        assertThat(cast.typeRef().name()).isEqualTo("Box");
        assertNode(cast, AstKind.CAST_EXPR, src, "v as Box");
    }

    @Test
    public void typeTestBindsLooserThanAddition() {
        String src = "func f(): Boolean {\n    return a + b is Int\n}\n";
        TypeTestExprNode test = (TypeTestExprNode) ret(onlyFunction(parseOk("istestadd.sol", src)), 0).value().orElseThrow();
        assertThat(binary(test.operand()).operator()).isEqualTo(BinaryOperator.ADD);
    }

    @Test
    public void castChainsAreLeftAssociative() {
        String src = "func f(): Admin {\n    return v as User as Admin\n}\n";
        CastExprNode outer = (CastExprNode) ret(onlyFunction(parseOk("castchain.sol", src)), 0).value().orElseThrow();
        assertThat(outer.typeRef().name()).isEqualTo("Admin");
        CastExprNode inner = (CastExprNode) outer.operand();
        assertThat(inner.typeRef().name()).isEqualTo("User");
    }

    @Test
    public void nullableTypeOperandIsParseable() {
        String src = "func f(v: Any): Boolean {\n    return v is String?\n}\n";
        TypeTestExprNode test = (TypeTestExprNode) ret(onlyFunction(parseOk("istestnull.sol", src)), 0).value().orElseThrow();
        assertThat(test.typeRef().isNullable()).isTrue();
    }

    @Test
    public void nullTerminatesAStatement() {
        String src = "func f(): Unit {\n    val x: Int? = null\n    val y: Int? = null\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("nullterm.sol", src));
        assertThat(fn.body().statements().size()).isEqualTo(2);
    }
}
