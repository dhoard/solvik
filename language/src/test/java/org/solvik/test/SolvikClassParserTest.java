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
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.solvik.ast.AstKind;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.ConstructorDeclNode;
import org.solvik.ast.declaration.DeclarationNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.PropertyDeclNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.ThisExprNode;
import org.solvik.ast.statement.AssignStmtNode;
import org.solvik.ast.statement.BindingKind;
import org.solvik.ast.statement.BlockNode;
import org.solvik.ast.statement.ReturnStmtNode;

/**
 * Phase 6 parser tests: class declarations, property declarations, constructors, instance methods,
 * and the {@code this} expression build the expected syntax AST with exact spans, including when
 * statement termination comes from semicolon insertion rather than explicit {@code ;}.
 */
public final class SolvikClassParserTest {

    private static ClassDeclNode onlyClass(CompilationUnitNode cu) {
        assertThat(cu.declarations().size()).isEqualTo(1);
        return (ClassDeclNode) cu.declarations().get(0);
    }

    @Test
    public void classWithPropertiesConstructorAndMethodHasTheExpectedShape() {
        String src = """
                class User {
                    val id: Integer
                    var name: String

                    User(id: Integer, name: String) {
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
        assertThat(user.name()).isEqualTo("User");
        assertThat(user.properties().size()).isEqualTo(2);

        PropertyDeclNode id = user.properties().get(0);
        assertNode(id, AstKind.PROPERTY_DECL, src, "val id: Integer");
        assertThat(id.bindingKind()).isEqualTo(BindingKind.VAL);
        assertThat(id.name()).isEqualTo("id");
        assertThat(id.declaredType().orElseThrow().name()).isEqualTo("Integer");
        assertThat(id.initializer().isEmpty()).isTrue();

        PropertyDeclNode name = user.properties().get(1);
        assertNode(name, AstKind.PROPERTY_DECL, src, "var name: String");
        assertThat(name.bindingKind()).isEqualTo(BindingKind.VAR);
        assertThat(name.declaredType().orElseThrow().name()).isEqualTo("String");

        assertThat(user.constructors().size()).isEqualTo(1);
        ConstructorDeclNode constructorDecl = user.constructor().orElseThrow();
        assertThat(constructorDecl.name()).isEqualTo("User");
        assertThat(constructorDecl.parameters().size()).isEqualTo(2);
        assertThat(constructorDecl.parameters().get(0).name()).isEqualTo("id");
        assertThat(constructorDecl.parameters().get(1).name()).isEqualTo("name");

        // constructor body: two `this.prop = param` assignments built from member access on `this`.
        BlockNode constructorBody = constructorDecl.body();
        assertThat(constructorBody.statements().size()).isEqualTo(2);
        AssignStmtNode first = (AssignStmtNode) constructorBody.statements().get(0);
        MemberAccessExprNode target = (MemberAccessExprNode) first.target();
        assertThat(target.memberName()).isEqualTo("id");
        assertThat(target.receiver() instanceof ThisExprNode).isTrue();
        assertNode(target.receiver(), AstKind.THIS_EXPR, src, "this");

        assertThat(user.methods().size()).isEqualTo(1);
        FunctionDeclNode describe = user.methods().get(0);
        assertThat(describe.name()).isEqualTo("describe");
        assertThat(describe.returnType().name()).isEqualTo("String");
        ReturnStmtNode ret = (ReturnStmtNode) describe.body().statements().get(0);
        MemberAccessExprNode value = (MemberAccessExprNode) ret.value().orElseThrow();
        assertThat(value.memberName()).isEqualTo("name");
        assertThat(value.receiver() instanceof ThisExprNode).isTrue();
    }

    @Test
    public void propertyInitializerIsParsedAndSpanned() {
        String src = "class Counter {\n    var count: Integer = 0\n}\n";
        ClassDeclNode counter = onlyClass(parseOk("counter.sol", src));
        PropertyDeclNode property = counter.properties().get(0);
        assertNode(property, AstKind.PROPERTY_DECL, src, "var count: Integer = 0");
        assertThat(property.initializer().isPresent()).isTrue();
        assertNode(property.initializer().get(), AstKind.INTEGER_LITERAL, src, "0");
    }

    @Test
    public void thisMethodCallBuildsACallOnThis() {
        String src = "class C {\n    func f(): Integer {\n        return this.g()\n    }\n}\n";
        ClassDeclNode c = onlyClass(parseOk("c.sol", src));
        ReturnStmtNode ret = (ReturnStmtNode) c.methods().get(0).body().statements().get(0);
        var call = SolvikTestSupport.call(ret.value().orElseThrow());
        MemberAccessExprNode callee = (MemberAccessExprNode) call.callee();
        assertThat(callee.memberName()).isEqualTo("g");
        assertThat(callee.receiver() instanceof ThisExprNode).isTrue();
    }

    @Test
    public void unqualifiedMethodCallWithinAClassParses() {
        String src = "class C {\n    func f(): Integer {\n        return g()\n    }\n}\n";
        ClassDeclNode c = onlyClass(parseOk("c.sol", src));
        ReturnStmtNode ret = (ReturnStmtNode) c.methods().get(0).body().statements().get(0);
        var call = SolvikTestSupport.call(ret.value().orElseThrow());
        assertThat(SolvikTestSupport.name(call.callee()).name()).isEqualTo("g");
    }

    @Test
    public void semicolonInsertionTerminatesClassMembers() {
        // No explicit `;` anywhere: property, constructor body, method body, and class body all rely
        // on newlines, so a constructor named after its class needs no insertion-table change.
        String src = """
                class C {
                    val value: Integer

                    C(value: Integer) {
                        this.value = value
                    }

                    func get(): Integer {
                        return this.value
                    }
                }
                    println(C(1).get())
                """;
        CompilationUnitNode cu = parseOk("inserted.sol", src);
        assertThat(cu.declarations().size()).isEqualTo(1);
        assertThat(cu.declarations().get(0) instanceof ClassDeclNode).isTrue();
        assertThat(cu.statements().size()).isEqualTo(1);
    }

    @Test
    public void classDeclarationsMayPrecedeOrFollowFunctions() {
        String src = """
                func use(): Integer {
                    return C(3).value()
                }
                class C {
                    val v: Integer
                    C(v: Integer) {
                        this.v = v
                    }
                    func value(): Integer {
                        return this.v
                    }
                }
                """;
        List<DeclarationNode> declarations = parseOk("order.sol", src).declarations();
        assertThat(declarations.size()).isEqualTo(2);
        assertThat(declarations.get(0) instanceof FunctionDeclNode).isTrue();
        assertThat(declarations.get(1) instanceof ClassDeclNode).isTrue();
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
                    val a: Integer = 1
                    val b: Integer = 2
                    func first(): Integer {
                        return this.a
                    }
                    func second(): Integer {
                        return this.b
                    }
                }
                """;
        ClassDeclNode c = onlyClass(parseOk("order2.sol", src));
        assertThat(c.properties().stream().map(PropertyDeclNode::name).toList()).isEqualTo(List.of("a", "b"));
        assertThat(c.methods().stream().map(FunctionDeclNode::name).toList()).isEqualTo(List.of("first", "second"));
    }
}
