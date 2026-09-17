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
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.DeclarationNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.InitDeclNode;
import org.solvik.ast.declaration.PropertyDeclNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.ThisExprNode;
import org.solvik.ast.statement.AssignStmtNode;
import org.solvik.ast.statement.BindingKind;
import org.solvik.ast.statement.BlockNode;
import org.solvik.ast.statement.ReturnStmtNode;

/**
 * Phase 6 parser tests: class declarations, property declarations, {@code init}, instance methods,
 * and the {@code this} expression build the expected syntax AST with exact spans, including when
 * statement termination comes from semicolon insertion rather than explicit {@code ;}.
 */
public final class SolvikClassParserTest {

    private static ClassDeclNode onlyClass(CompilationUnitNode cu) {
        assertEquals(1, cu.declarations().size());
        return (ClassDeclNode) cu.declarations().get(0);
    }

    @Test
    public void classWithPropertiesInitAndMethodHasTheExpectedShape() {
        String src = """
                class User {
                    val id: Int
                    var name: String

                    init(id: Int, name: String) {
                        this.id = id
                        this.name = name
                    }

                    func describe(): String {
                        return this.name
                    }
                }
                """;
        ClassDeclNode user = onlyClass(parseOk("user.sol", src));
        assertNode(user, AstKind.CLASS_DECL, src, src.substring(src.indexOf("class"), src.lastIndexOf('}') + 1));
        assertEquals("User", user.name());
        assertEquals(2, user.properties().size());

        PropertyDeclNode id = user.properties().get(0);
        assertNode(id, AstKind.PROPERTY_DECL, src, "val id: Int");
        assertEquals(BindingKind.VAL, id.bindingKind());
        assertEquals("id", id.name());
        assertEquals("Int", id.declaredType().orElseThrow().name());
        assertTrue(id.initializer().isEmpty());

        PropertyDeclNode name = user.properties().get(1);
        assertNode(name, AstKind.PROPERTY_DECL, src, "var name: String");
        assertEquals(BindingKind.VAR, name.bindingKind());
        assertEquals("String", name.declaredType().orElseThrow().name());

        assertEquals(1, user.initializers().size());
        InitDeclNode init = user.initializer().orElseThrow();
        assertEquals(2, init.parameters().size());
        assertEquals("id", init.parameters().get(0).name());
        assertEquals("name", init.parameters().get(1).name());

        // init body: two `this.prop = param` assignments built from member access on `this`.
        BlockNode initBody = init.body();
        assertEquals(2, initBody.statements().size());
        AssignStmtNode first = (AssignStmtNode) initBody.statements().get(0);
        MemberAccessExprNode target = (MemberAccessExprNode) first.target();
        assertEquals("id", target.memberName());
        assertTrue(target.receiver() instanceof ThisExprNode);
        assertNode(target.receiver(), AstKind.THIS_EXPR, src, "this");

        assertEquals(1, user.methods().size());
        FunctionDeclNode describe = user.methods().get(0);
        assertEquals("describe", describe.name());
        assertEquals("String", describe.returnType().name());
        ReturnStmtNode ret = (ReturnStmtNode) describe.body().statements().get(0);
        MemberAccessExprNode value = (MemberAccessExprNode) ret.value().orElseThrow();
        assertEquals("name", value.memberName());
        assertTrue(value.receiver() instanceof ThisExprNode);
    }

    @Test
    public void propertyInitializerIsParsedAndSpanned() {
        String src = "class Counter {\n    var count: Int = 0\n}\n";
        ClassDeclNode counter = onlyClass(parseOk("counter.sol", src));
        PropertyDeclNode property = counter.properties().get(0);
        assertNode(property, AstKind.PROPERTY_DECL, src, "var count: Int = 0");
        assertTrue(property.initializer().isPresent());
        assertNode(property.initializer().get(), AstKind.INT_LITERAL, src, "0");
    }

    @Test
    public void thisMethodCallBuildsACallOnThis() {
        String src = "class C {\n    func f(): Int {\n        return this.g()\n    }\n}\n";
        ClassDeclNode c = onlyClass(parseOk("c.sol", src));
        ReturnStmtNode ret = (ReturnStmtNode) c.methods().get(0).body().statements().get(0);
        var call = SolvikTestSupport.call(ret.value().orElseThrow());
        MemberAccessExprNode callee = (MemberAccessExprNode) call.callee();
        assertEquals("g", callee.memberName());
        assertTrue(callee.receiver() instanceof ThisExprNode);
    }

    @Test
    public void unqualifiedMethodCallWithinAClassParses() {
        String src = "class C {\n    func f(): Int {\n        return g()\n    }\n}\n";
        ClassDeclNode c = onlyClass(parseOk("c.sol", src));
        ReturnStmtNode ret = (ReturnStmtNode) c.methods().get(0).body().statements().get(0);
        var call = SolvikTestSupport.call(ret.value().orElseThrow());
        assertEquals("g", SolvikTestSupport.name(call.callee()).name());
    }

    @Test
    public void semicolonInsertionTerminatesClassMembers() {
        // No explicit `;` anywhere: property, method body, and class body all rely on newlines.
        String src = """
                class C {
                    val value: Int = 1

                    func get(): Int {
                        return this.value
                    }
                }
                func main(): Unit {
                    println(C().get())
                }
                """;
        CompilationUnitNode cu = parseOk("inserted.sol", src);
        assertEquals(2, cu.declarations().size());
        assertTrue(cu.declarations().get(0) instanceof ClassDeclNode);
        assertTrue(cu.declarations().get(1) instanceof FunctionDeclNode);
    }

    @Test
    public void classDeclarationsMayPrecedeOrFollowFunctions() {
        String src = """
                func use(): Int {
                    return C(3).value()
                }
                class C {
                    val v: Int
                    init(v: Int) {
                        this.v = v
                    }
                    func value(): Int {
                        return this.v
                    }
                }
                """;
        List<DeclarationNode> declarations = parseOk("order.sol", src).declarations();
        assertEquals(2, declarations.size());
        assertTrue(declarations.get(0) instanceof FunctionDeclNode);
        assertTrue(declarations.get(1) instanceof ClassDeclNode);
    }

    @Test
    public void thisAtEndOfLineTerminatesTheStatement() {
        String src = """
                class C {
                    func self(): C {
                        return this
                    }
                }
                """;
        ClassDeclNode c = onlyClass(parseOk("self.sol", src));
        ReturnStmtNode ret = (ReturnStmtNode) c.methods().get(0).body().statements().get(0);
        assertNode(ret.value().orElseThrow(), AstKind.THIS_EXPR, src, "this");
    }

    @Test
    public void multiplePropertiesAndMethodsKeepSourceOrder() {
        String src = """
                class C {
                    val a: Int = 1
                    val b: Int = 2
                    func first(): Int {
                        return this.a
                    }
                    func second(): Int {
                        return this.b
                    }
                }
                """;
        ClassDeclNode c = onlyClass(parseOk("order2.sol", src));
        assertEquals(List.of("a", "b"), c.properties().stream().map(PropertyDeclNode::name).toList());
        assertEquals(List.of("first", "second"), c.methods().stream().map(FunctionDeclNode::name).toList());
    }
}
