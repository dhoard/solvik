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
import static org.solvik.test.SolvikTestSupport.assertNode;
import static org.solvik.test.SolvikTestSupport.binary;
import static org.solvik.test.SolvikTestSupport.local;
import static org.solvik.test.SolvikTestSupport.member;
import static org.solvik.test.SolvikTestSupport.name;
import static org.solvik.test.SolvikTestSupport.onlyFunction;
import static org.solvik.test.SolvikTestSupport.parseOk;
import static org.solvik.test.SolvikTestSupport.ret;

import org.junit.Test;
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
        assertEquals("String", type.name());
        assertTrue(type.isNullable());
        assertNode(type, AstKind.TYPE_REF, src, "String?");
    }

    @Test
    public void nonNullableTypeReferenceStaysNonNullable() {
        FunctionDeclNode fn = onlyFunction(parseOk("plainparam.sol", "func f(name: String): Unit {\n}\n"));
        assertFalse(fn.parameters().get(0).type().isNullable());
    }

    @Test
    public void nullableLocalTypeIsRecorded() {
        String src = "func f(): Unit {\n    val x: Int? = null\n}\n";
        LocalDeclNode declaration = local(onlyFunction(parseOk("nulllocal.sol", src)), 0);
        assertTrue(declaration.declaredType().orElseThrow().isNullable());
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
        assertTrue(access.isSafe());
        assertEquals("value", access.memberName());
        assertNode(access, AstKind.MEMBER_ACCESS_EXPR, src, "box?.value");
    }

    @Test
    public void ordinaryDotIsNotSafe() {
        FunctionDeclNode fn = onlyFunction(parseOk("plainmember.sol", "func f(box: Box): Int {\n    return box.value\n}\n"));
        assertFalse(member(ret(fn, 0).value().orElseThrow()).isSafe());
    }

    @Test
    public void nullableDotChainStaysOneMemberAccess() {
        String src = "func f(a: A?): C? {\n    return a?.b?.c\n}\n";
        MemberAccessExprNode outer = member(ret(onlyFunction(parseOk("safechain.sol", src)), 0).value().orElseThrow());
        assertEquals("c", outer.memberName());
        MemberAccessExprNode inner = member(outer.receiver());
        assertEquals("b", inner.memberName());
        assertTrue(inner.isSafe() && outer.isSafe());
    }

    @Test
    public void coalescingBindsLooserThanLogicalOr() {
        String src = "func f(): Int {\n    val x = a ?? b || c\n}\n";
        LocalDeclNode declaration = local(onlyFunction(parseOk("coalesce.sol", src)), 0);
        BinaryExprNode coalesce = binary(declaration.initializer());
        assertEquals(BinaryOperator.COALESCE, coalesce.operator());
        assertEquals(BinaryOperator.OR, binary(coalesce.right()).operator());
    }

    @Test
    public void coalescingIsLeftAssociative() {
        String src = "func f(): Int {\n    val x = a ?? b ?? c\n}\n";
        BinaryExprNode outer = binary(local(onlyFunction(parseOk("coalescechain.sol", src)), 0).initializer());
        assertEquals(BinaryOperator.COALESCE, outer.operator());
        assertEquals(BinaryOperator.COALESCE, binary(outer.left()).operator());
        assertEquals("c", name(outer.right()).name());
    }

    @Test
    public void typeTestProducesADedicatedNode() {
        String src = "func f(v: Any): Boolean {\n    return v is Box\n}\n";
        TypeTestExprNode test = (TypeTestExprNode) ret(onlyFunction(parseOk("istest.sol", src)), 0).value().orElseThrow();
        assertEquals("Box", test.typeRef().name());
        assertNode(test, AstKind.TYPE_TEST_EXPR, src, "v is Box");
    }

    @Test
    public void checkedCastProducesADedicatedNode() {
        String src = "func f(v: Any): Box {\n    return v as Box\n}\n";
        CastExprNode cast = (CastExprNode) ret(onlyFunction(parseOk("cast.sol", src)), 0).value().orElseThrow();
        assertEquals("Box", cast.typeRef().name());
        assertNode(cast, AstKind.CAST_EXPR, src, "v as Box");
    }

    @Test
    public void typeTestBindsLooserThanAddition() {
        String src = "func f(): Boolean {\n    return a + b is Int\n}\n";
        TypeTestExprNode test = (TypeTestExprNode) ret(onlyFunction(parseOk("istestadd.sol", src)), 0).value().orElseThrow();
        assertEquals(BinaryOperator.ADD, binary(test.operand()).operator());
    }

    @Test
    public void castChainsAreLeftAssociative() {
        String src = "func f(): Admin {\n    return v as User as Admin\n}\n";
        CastExprNode outer = (CastExprNode) ret(onlyFunction(parseOk("castchain.sol", src)), 0).value().orElseThrow();
        assertEquals("Admin", outer.typeRef().name());
        CastExprNode inner = (CastExprNode) outer.operand();
        assertEquals("User", inner.typeRef().name());
    }

    @Test
    public void nullableTypeOperandIsParseable() {
        String src = "func f(v: Any): Boolean {\n    return v is String?\n}\n";
        TypeTestExprNode test = (TypeTestExprNode) ret(onlyFunction(parseOk("istestnull.sol", src)), 0).value().orElseThrow();
        assertTrue(test.typeRef().isNullable());
    }

    @Test
    public void nullTerminatesAStatement() {
        String src = "func f(): Unit {\n    val x: Int? = null\n    val y: Int? = null\n}\n";
        FunctionDeclNode fn = onlyFunction(parseOk("nullterm.sol", src));
        assertEquals(2, fn.body().statements().size());
    }
}
