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
import static org.solvik.test.SolvikTestSupport.parseFails;
import static org.solvik.test.SolvikTestSupport.parseOk;

import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.expression.MemberAccessExprNode;
import org.solvik.ast.expression.SuperExprNode;
import org.solvik.ast.statement.ExprStmtNode;

/**
 * Phase 7 parser tests: {@code open class}, {@code extends}, {@code open}/{@code override} method
 * modifiers, the {@code super(...)} constructor call, and {@code super.member} access. The grammar
 * permits at most one {@code extends} clause, so multiple inheritance is a parse error.
 */
public final class SolvikInheritanceParserTest {

    @Test
    public void openClassWithSingleSuperclassParses() {
        CompilationUnitNode unit = parseOk("inherit.sol", "open class Animal {\n}\nclass Dog extends Animal {\n}\n");
        assertThat(unit.declarations().size()).isEqualTo(2);
        ClassDeclNode animal = (ClassDeclNode) unit.declarations().get(0);
        assertThat(animal.isOpen()).isTrue();
        assertThat(animal.name()).isEqualTo("Animal");
        assertThat(animal.superClass().isEmpty()).isTrue();

        ClassDeclNode dog = (ClassDeclNode) unit.declarations().get(1);
        assertThat(dog.isOpen()).isFalse();
        assertThat(dog.name()).isEqualTo("Dog");
        assertThat(dog.superClass().isPresent()).isTrue();
        assertThat(dog.superClass().orElseThrow().name()).isEqualTo("Animal");
    }

    @Test
    public void methodModifiersAreRecorded() {
        CompilationUnitNode unit = parseOk("mods.sol", """
                open class Animal {
                    open func speak(): String {
                        return "..."
                    }
                }
                class Dog extends Animal {
                    override func speak(): String {
                        return "woof"
                    }
                }
                """);
        ClassDeclNode animal = (ClassDeclNode) unit.declarations().get(0);
        FunctionDeclNode inherited = animal.methods().get(0);
        assertThat(inherited.isOpen()).isTrue();
        assertThat(inherited.isOverride()).isFalse();

        ClassDeclNode dog = (ClassDeclNode) unit.declarations().get(1);
        FunctionDeclNode overriding = dog.methods().get(0);
        assertThat(overriding.isOpen()).isFalse();
        assertThat(overriding.isOverride()).isTrue();
    }

    @Test
    public void explicitSuperConstructorCallParsesAsASuperExprCall() {
        CompilationUnitNode unit = parseOk("super.sol", """
                open class Animal {
                    val legs: Int
                    Animal(legs: Int) {
                        this.legs = legs
                    }
                }
                class Dog extends Animal {
                    Dog() {
                        super(4)
                    }
                }
                """);
        ClassDeclNode dog = (ClassDeclNode) unit.declarations().get(1);
        ExprStmtNode first = (ExprStmtNode) dog.constructor().orElseThrow().body().statements().get(0);
        CallExprNode call = (CallExprNode) first.expression();
        assertThat(call.callee() instanceof SuperExprNode).isTrue();
        assertThat(call.arguments().size()).isEqualTo(1);
    }

    @Test
    public void superMemberAccessParsesAsASuperExprReceiver() {
        CompilationUnitNode unit = parseOk("supermember.sol", """
                open class Animal {
                    open func speak(): String {
                        return "..."
                    }
                }
                class Dog extends Animal {
                    override func speak(): String {
                        return super.speak()
                    }
                }
                """);
        ClassDeclNode dog = (ClassDeclNode) unit.declarations().get(1);
        FunctionDeclNode speak = dog.methods().get(0);
        CallExprNode call = (CallExprNode) ((org.solvik.ast.statement.ReturnStmtNode) speak.body().statements().get(0)).value().orElseThrow();
        MemberAccessExprNode member = (MemberAccessExprNode) call.callee();
        assertThat(member.receiver() instanceof SuperExprNode).isTrue();
        assertThat(member.memberName()).isEqualTo("speak");
    }

    @Test
    public void multipleInheritanceIsRejectedByTheGrammar() {
        parseFails("multi.sol", "open class A {\n}\nopen class B {\n}\nclass C extends A, B {\n}\n");
    }

    @Test
    public void aTopLevelFunctionCannotCarryMethodModifiers() {
        parseFails("topmod.sol", "open func f(): Int {\n    return 1\n}\n");
    }
}
