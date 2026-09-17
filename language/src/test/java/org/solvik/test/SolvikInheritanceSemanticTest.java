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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.parseOk;

import org.junit.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.expression.CallExprNode;
import org.solvik.ast.statement.ExprStmtNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.ClassSymbol;
import org.solvik.semantic.FunctionSymbol;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * Positive Phase 7 semantic tests: single inheritance joins the nominal hierarchy, inherited
 * members resolve, overrides are recorded in the virtual dispatch table, and both explicit and
 * implicit {@code super} initializer calls are recognized.
 */
public final class SolvikInheritanceSemanticTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("inherit.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertTrue("analysis must succeed: " + result.diagnostics().all(), result.isSuccess());
        return result.requireProgram();
    }

    @Test
    public void subclassJoinsTheNominalHierarchy() {
        CheckedProgram program = check("open class Animal {\n}\nclass Dog extends Animal {\n}\n");
        ClassSymbol animal = program.classSymbol("Animal").orElseThrow();
        ClassSymbol dog = program.classSymbol("Dog").orElseThrow();
        assertEquals(animal, dog.superClass().orElseThrow());
        assertTrue(dog.type().isSubtypeOf(animal.type()));
        assertTrue(dog.type().isSubtypeOf(org.solvik.type.ObjectType.INSTANCE));
        assertTrue(animal.type().isSubtypeOf(org.solvik.type.ObjectType.INSTANCE));
        assertFalse(animal.type().isSubtypeOf(dog.type()));
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
        assertTrue(inherited.isOpen());
        assertTrue(overriding.isOverride());
        assertFalse(inherited == overriding);
        assertEquals(overriding, dog.declaredMethods().get(0));
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
        assertEquals(program.classSymbol("Animal").orElseThrow(), program.superConstructorOf(call).orElseThrow());
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
        assertTrue(program.methodOf(call).orElseThrow().isSuperCall());
        assertEquals(program.classSymbol("Animal").orElseThrow().method("speak").orElseThrow(), program.methodOf(call).orElseThrow().method());
    }

    @Test
    public void extendsObjectIsExplicitlyAllowed() {
        CheckedProgram program = check("class Plain extends Object {\n}\n");
        ClassSymbol plain = program.classSymbol("Plain").orElseThrow();
        assertTrue(plain.superClass().isEmpty());
        assertTrue(plain.type().isSubtypeOf(org.solvik.type.ObjectType.INSTANCE));
    }
}
