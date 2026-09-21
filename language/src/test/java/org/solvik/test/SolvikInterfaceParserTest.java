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

import java.util.List;
import org.junit.jupiter.api.Test;
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
                        return "Hello " .. name()
                    }
                }
                """);
        InterfaceDeclNode named = (InterfaceDeclNode) unit.declarations().get(0);
        assertThat(named.name()).isEqualTo("Named");
        assertThat(named.signatures().size()).isEqualTo(1);
        assertThat(named.defaultMethods().size()).isEqualTo(1);

        SignatureDeclNode name = named.signatures().get(0);
        assertThat(name.name()).isEqualTo("name");
        assertThat(name.hasBody()).isFalse();
        assertThat(name.returnType().name()).isEqualTo("String");

        FunctionDeclNode greeting = named.defaultMethods().get(0);
        assertThat(greeting.name()).isEqualTo("greeting");
        assertThat(greeting.hasBody()).isTrue();
        assertThat(greeting.body().statements().size()).isEqualTo(1);
    }

    @Test
    public void multipleImplementsIsParsedInSourceOrder() {
        CompilationUnitNode unit = parseOk("multi.sol", """
                interface A {
                    func a(): Integer
                }
                interface B {
                    func b(): Integer
                }
                class C implements A, B {
                    func a(): Integer {
                        return 1
                    }

                    func b(): Integer {
                        return 2
                    }
                }
                """);
        ClassDeclNode c = (ClassDeclNode) unit.declarations().get(2);
        assertThat(c.interfaces().stream().map(t -> t.name()).toList()).isEqualTo(List.of("A", "B"));
        assertThat(c.methods().size()).isEqualTo(2);
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
        assertThat(stream.superInterfaces().stream().map(t -> t.name()).toList()).isEqualTo(List.of("Readable", "Writable"));
        // Stream declares no signature of its own; Readable and Writable requirements are inherited.
        assertThat(stream.signatures().size()).isEqualTo(0);
        assertThat(stream.defaultMethods().size()).isEqualTo(1);
    }

    @Test
    public void classCombinesExtendsAndImplements() {
        CompilationUnitNode unit = parseOk("both.sol", """
                interface Named {
                    func name(): String
                }
                open class Base {
                    val id: Integer

                    Base(id: Integer) {
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
        assertThat(user.superClass().orElseThrow().name()).isEqualTo("Base");
        assertThat(user.interfaces().stream().map(t -> t.name()).toList()).isEqualTo(List.of("Named"));
    }

    @Test
    public void interfaceMemberSignaturesSemiTerminateAndDefaultBodiesBraceTerminate() {
        // A signature ends in an explicit `;`; a default method body ends in `}`, which is itself a
        // semicolon-insertion terminator, so the grammar tolerates the synthesized `;` after it.
        CompilationUnitNode unit = parseOk("terminators.sol", "interface I {\n    func a(): Integer\n    func b(): Integer {\n        return 1\n    }\n\n    func c(): Integer {\n        return 2\n    }\n}\n");
        InterfaceDeclNode declaration = (InterfaceDeclNode) unit.declarations().get(0);
        assertThat(declaration.signatures().size()).isEqualTo(1);
        assertThat(declaration.defaultMethods().size()).isEqualTo(2);
    }

    @Test
    public void declarationOrderIsPreservedAcrossFunctionsClassesAndInterfaces() {
        CompilationUnitNode unit = parseOk("order.sol", "interface I {\n    func f(): Integer\n}\nfunc g(): Integer {\n    return 1\n}\nclass C implements I {\n    func f(): Integer {\n        return 2\n    }\n}\n");
        assertThat(unit.declarations().stream().map(d -> d instanceof InterfaceDeclNode i ? i.name() : d instanceof ClassDeclNode c ? c.name() : ((FunctionDeclNode) d).name()).toList()).isEqualTo(List.of("I", "g", "C"));
    }

    @Test
    public void propertyDeclarationInsideAnInterfaceIsRejected() {
        parseFails("property.sol", "interface I {\n    val value: Integer\n}\n");
    }

    @Test
    public void constructorDeclarationInsideAnInterfaceIsRejected() {
        parseFails("constructor.sol", "interface I {\n    I() {\n    }\n}\n");
    }

    @Test
    public void interfaceMemberCarriesNoOverrideModifier() {
        parseFails("modifier.sol", "interface I {\n    override func f(): Integer {\n        return 1\n    }\n}\n");
    }

    @Test
    public void implementsIsNotValidOnAnInterface() {
        parseFails("implements.sol", "interface A {\n    func a(): Integer\n}\ninterface B implements A {\n}\n");
    }

    @Test
    public void interfaceMemberMayOmitItsReturnType() {
        CompilationUnitNode unit = parseOk("notype.sol", "interface I {\n    func f()\n}\n");
        InterfaceDeclNode i = (InterfaceDeclNode) unit.declarations().get(0);
        // An omitted return type is Unit (docs/LANGUAGE_SPEC.md section 6).
        assertThat(i.signatures().get(0).returnType().name()).isEqualTo("Unit");
    }

    @Test
    public void interfaceMemberReturnTypeColonRequiresAType() {
        parseFails("notype2.sol", "interface I {\n    func f():\n}\n");
    }
}
