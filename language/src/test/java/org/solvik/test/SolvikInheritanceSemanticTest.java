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
import static org.solvik.test.SolvikTestSupport.parseOk;

import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.statement.ExprStmtNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.ClassSymbol;
import org.solvik.semantic.FunctionSymbol;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.AnyType;
import org.solvik.type.Type;

/**
 * Positive Phase 7 semantic tests: single inheritance joins the nominal hierarchy, inherited
 * members resolve, overrides are recorded in the virtual dispatch table, and both explicit and
 * implicit {@code super} initializer calls are recognized.
 */
public final class SolvikInheritanceSemanticTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("inherit.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
        return result.requireProgram();
    }

    @Test
    public void subclassJoinsTheNominalHierarchy() {
        CheckedProgram program = check("open class Animal {\n}\nclass Dog extends Animal {\n}\n");
        ClassSymbol animal = program.classSymbol("Animal").orElseThrow();
        ClassSymbol dog = program.classSymbol("Dog").orElseThrow();
        assertThat(dog.superClass().orElseThrow()).isEqualTo(animal);
        assertThat(dog.type().isSubtypeOf(animal.type())).isTrue();
        assertThat(dog.type().isSubtypeOf(AnyType.INSTANCE)).isTrue();
        assertThat(animal.type().isSubtypeOf(AnyType.INSTANCE)).isTrue();
        assertThat(animal.type().isSubtypeOf(dog.type())).isFalse();
    }

    @Test
    public void inheritedPropertiesAndMethodsAreVisible() {
        check("""
                open class Animal {
                    val name: String

                    Animal(name: String) {
                        this.name = name
                    }

                    func describe(): String {
                        return this.name
                    }
                }
                class Dog extends Animal {
                    Dog() {
                        super("Rex")
                    }
                }
                func f(): String {
                    val dog = Dog()
                    return dog.describe()
                }
                """);
    }

    @Test
    public void overrideReplacesTheInheritedMethodInTheDispatchTable() {
        CheckedProgram program = check("""
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
        ClassSymbol animal = program.classSymbol("Animal").orElseThrow();
        ClassSymbol dog = program.classSymbol("Dog").orElseThrow();
        FunctionSymbol inherited = animal.method("speak").orElseThrow();
        FunctionSymbol overriding = dog.method("speak").orElseThrow();
        assertThat(inherited.isOpen()).isTrue();
        assertThat(overriding.isOverride()).isTrue();
        assertThat(inherited == overriding).isFalse();
        assertThat(dog.declaredMethods().get(0)).isEqualTo(overriding);
    }

    @Test
    public void explicitSuperConstructorCallIsRecognized() {
        CheckedProgram program = check("""
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
        ClassDeclNode dog = (ClassDeclNode) program.unit().declarations().get(1);
        ExprStmtNode first = (ExprStmtNode) dog.constructor().orElseThrow().body().statements().get(0);
        CallExprNode call = (CallExprNode) first.expression();
        assertThat(program.superConstructorOf(call).orElseThrow()).isEqualTo(program.classSymbol("Animal").orElseThrow());
    }

    @Test
    public void implicitSuperConstructorCallIsAcceptedForAZeroArgumentSuperclass() {
        check("""
                open class Animal {
                    val legs: Int = 4
                }
                class Dog extends Animal {
                    val name: String
                    Dog(name: String) {
                        this.name = name
                    }
                }
                """);
    }

    @Test
    public void superMemberAccessTargetsTheSuperclassImplementation() {
        CheckedProgram program = check("""
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
        ClassDeclNode dog = (ClassDeclNode) program.unit().declarations().get(1);
        org.solvik.ast.statement.ReturnStmtNode statement = (org.solvik.ast.statement.ReturnStmtNode) dog.methods().get(0).body().statements().get(0);
        CallExprNode call = (CallExprNode) statement.value().orElseThrow();
        assertThat(program.methodOf(call).orElseThrow().isSuperCall()).isTrue();
        assertThat(program.methodOf(call).orElseThrow().method()).isEqualTo(program.classSymbol("Animal").orElseThrow().method("speak").orElseThrow());
    }

    @Test
    public void extendsAnyIsExplicitlyAllowed() {
        CheckedProgram program = check("class Plain extends Any {\n}\n");
        ClassSymbol plain = program.classSymbol("Plain").orElseThrow();
        assertThat(plain.superClass().isEmpty()).isTrue();
        assertThat(plain.type().superType().orElseThrow()).isSameAs(AnyType.INSTANCE);
        assertThat(plain.type().isSubtypeOf(AnyType.INSTANCE)).isTrue();
    }

    @Test
    public void aClassWithNoWrittenSuperclassDerivesDirectlyFromAny() {
        CheckedProgram program = check("class Plain {\n}\n");
        ClassSymbol plain = program.classSymbol("Plain").orElseThrow();
        assertThat(plain.superClass().isEmpty()).isTrue();
        assertThat(plain.type().superType().orElseThrow()).isSameAs(AnyType.INSTANCE);
        assertThat(AnyType.INSTANCE.isSubtypeOf(plain.type())).isFalse();
    }

    @Test
    public void userDeclaredObjectIsAnOrdinaryNominalTypeUnderAny() {
        CheckedProgram program = check("open class Object {\n}\nclass User extends Object {\n}\n");
        ClassSymbol object = program.classSymbol("Object").orElseThrow();
        ClassSymbol user = program.classSymbol("User").orElseThrow();
        assertThat(object.superClass().isEmpty()).isTrue();
        assertThat(object.type().superType().orElseThrow()).isSameAs(AnyType.INSTANCE);
        assertThat(user.superClass().orElseThrow()).isSameAs(object);
        assertThat(user.type().isSubtypeOf(object.type())).isTrue();
        assertThat(object.type().isSubtypeOf(user.type())).isFalse();
    }

    @Test
    public void interfaceNamedObjectIsAnOrdinaryNominalType() {
        CheckedProgram program = check("""
                interface Object {
                    func size(): Int
                }
                class Bag implements Object {
                    func size(): Int {
                        return 0
                    }
                }
                """);
        Type object = program.interfaceSymbol("Object").orElseThrow().type();
        ClassSymbol bag = program.classSymbol("Bag").orElseThrow();
        assertThat(object.superType().orElseThrow()).isSameAs(AnyType.INSTANCE);
        assertThat(object.isSubtypeOf(AnyType.INSTANCE)).isTrue();
        assertThat(bag.type().isSubtypeOf(object)).isTrue();
    }

    @Test
    public void enumNamedObjectIsAnOrdinaryNominalType() {
        CheckedProgram program = check("""
                enum Object {
                    Only
                }
                """);
        Type object = program.enumSymbol("Object").orElseThrow().type();
        assertThat(object.superType().orElseThrow()).isSameAs(AnyType.INSTANCE);
        assertThat(object.isSubtypeOf(AnyType.INSTANCE)).isTrue();
    }

    @Test
    public void genericClassExtendingAnyDerivesDirectlyFromAny() {
        CheckedProgram program = check("class Box<T> extends Any {\n}\n");
        ClassSymbol box = program.classSymbol("Box").orElseThrow();
        assertThat(box.superClass().isEmpty()).isTrue();
        assertThat(box.type().superType().orElseThrow()).isSameAs(AnyType.INSTANCE);
    }
}
