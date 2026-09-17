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
import static org.solvik.test.SolvikTestSupport.parseFails;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.ClassDeclNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.declaration.InterfaceDeclNode;
import org.solvik.ast.declaration.SignatureDeclNode;

/**
 * Phase 8 parser tests: {@code interface} declarations with abstract signatures and default methods,
 * interface {@code extends} lists, and a class {@code implements} list. The grammar keeps interfaces
 * method-only, so a property or constructor inside an interface body is a parse error.
 */
public final class SolvikInterfaceParserTest {

    @Test
    public void specificationInterfaceShapeParses() {
        CompilationUnitNode unit = parseOk("named.sol", """
                interface Named {
                    func name(): String

                    func greeting(): String {
                        return "Hello " + name()
                    }
                }
                """);
        InterfaceDeclNode named = (InterfaceDeclNode) unit.declarations().get(0);
        assertEquals("Named", named.name());
        assertEquals(1, named.signatures().size());
        assertEquals(1, named.defaultMethods().size());

        SignatureDeclNode name = named.signatures().get(0);
        assertEquals("name", name.name());
        assertFalse(name.hasBody());
        assertEquals("String", name.returnType().name());

        FunctionDeclNode greeting = named.defaultMethods().get(0);
        assertEquals("greeting", greeting.name());
        assertTrue(greeting.hasBody());
        assertEquals(1, greeting.body().statements().size());
    }

    @Test
    public void multipleImplementsIsParsedInSourceOrder() {
        CompilationUnitNode unit = parseOk("multi.sol", """
                interface A {
                    func a(): Int
                }
                interface B {
                    func b(): Int
                }
                class C implements A, B {
                    func a(): Int {
                        return 1
                    }

                    func b(): Int {
                        return 2
                    }
                }
                """);
        ClassDeclNode c = (ClassDeclNode) unit.declarations().get(2);
        assertEquals(List.of("A", "B"), c.interfaces().stream().map(t -> t.name()).toList());
        assertEquals(2, c.methods().size());
    }

    @Test
    public void interfaceExtendsMultipleInterfaces() {
        CompilationUnitNode unit = parseOk("extend.sol", """
                interface Readable {
                    func read(): String
                }
                interface Writable {
                    func write(value: String): Unit
                }
                interface Stream extends Readable, Writable {
                    func describe(): String {
                        return read()
                    }
                }
                """);
        InterfaceDeclNode stream = (InterfaceDeclNode) unit.declarations().get(2);
        assertEquals(List.of("Readable", "Writable"), stream.superInterfaces().stream().map(t -> t.name()).toList());
        // Stream declares no signature of its own; Readable and Writable requirements are inherited.
        assertEquals(0, stream.signatures().size());
        assertEquals(1, stream.defaultMethods().size());
    }

    @Test
    public void classCombinesExtendsAndImplements() {
        CompilationUnitNode unit = parseOk("both.sol", """
                interface Named {
                    func name(): String
                }
                open class Base {
                    val id: Int

                    Base(id: Int) {
                        this.id = id
                    }
                }
                class User extends Base implements Named {
                    User() {
                        super(1)
                    }

                    func name(): String {
                        return "user"
                    }
                }
                """);
        ClassDeclNode user = (ClassDeclNode) unit.declarations().get(2);
        assertEquals("Base", user.superClass().orElseThrow().name());
        assertEquals(List.of("Named"), user.interfaces().stream().map(t -> t.name()).toList());
    }

    @Test
    public void interfaceMemberSignaturesSemiTerminateAndDefaultBodiesBraceTerminate() {
        // A signature ends in an explicit `;`; a default method body ends in `}`, which is itself a
        // semicolon-insertion terminator, so the grammar tolerates the synthesized `;` after it.
        CompilationUnitNode unit = parseOk("terminators.sol", "interface I {\n    func a(): Int\n    func b(): Int {\n        return 1\n    }\n\n    func c(): Int {\n        return 2\n    }\n}\n");
        InterfaceDeclNode declaration = (InterfaceDeclNode) unit.declarations().get(0);
        assertEquals(1, declaration.signatures().size());
        assertEquals(2, declaration.defaultMethods().size());
    }

    @Test
    public void declarationOrderIsPreservedAcrossFunctionsClassesAndInterfaces() {
        CompilationUnitNode unit = parseOk("order.sol", "interface I {\n    func f(): Int\n}\nfunc g(): Int {\n    return 1\n}\nclass C implements I {\n    func f(): Int {\n        return 2\n    }\n}\n");
        assertEquals(List.of("I", "g", "C"), unit.declarations().stream().map(d -> d instanceof InterfaceDeclNode i ? i.name() : d instanceof ClassDeclNode c ? c.name() : ((FunctionDeclNode) d).name()).toList());
    }

    @Test
    public void propertyDeclarationInsideAnInterfaceIsRejected() {
        parseFails("property.sol", "interface I {\n    val value: Int\n}\n");
    }

    @Test
    public void constructorDeclarationInsideAnInterfaceIsRejected() {
        parseFails("constructor.sol", "interface I {\n    I() {\n    }\n}\n");
    }

    @Test
    public void interfaceMemberCarriesNoOverrideModifier() {
        parseFails("modifier.sol", "interface I {\n    override func f(): Int {\n        return 1\n    }\n}\n");
    }

    @Test
    public void implementsIsNotValidOnAnInterface() {
        parseFails("implements.sol", "interface A {\n    func a(): Int\n}\ninterface B implements A {\n}\n");
    }

    @Test
    public void anInterfaceMemberRequiresAReturnType() {
        parseFails("notype.sol", "interface I {\n    func f()\n}\n");
    }
}
