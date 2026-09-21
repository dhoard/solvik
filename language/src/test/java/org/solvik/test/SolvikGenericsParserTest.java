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
import org.solvik.ast.declaration.InterfaceDeclNode;
import org.solvik.ast.declaration.PropertyDeclNode;
import org.solvik.ast.declaration.SignatureDeclNode;
import org.solvik.ast.declaration.TypeRefNode;

/**
 * Phase 11 parser tests (docs/LANGUAGE_SPEC.md section 11): type parameter lists on generic
 * declarations and generic type applications in written types.
 */
public final class SolvikGenericsParserTest {

    @Test
    public void classTypeParameterListIsRecorded() {
        CompilationUnitNode unit = parseOk("g.sol", """
                class Box<T> {
                    var value: T
                }
                """);
        ClassDeclNode box = (ClassDeclNode) unit.declarations().get(0);
        assertThat(box.typeParameters().size()).isEqualTo(1);
        assertThat(box.typeParameters().get(0).name()).isEqualTo("T");
        PropertyDeclNode property = box.properties().get(0);
        assertThat(property.name()).isEqualTo("value");
        assertThat(property.declaredType().orElseThrow().name()).isEqualTo("T");
    }

    @Test
    public void multipleTypeParametersKeepSourceOrder() {
        CompilationUnitNode unit = parseOk("g.sol", """
                class Pair<K, V> {
                    var first: K
                    var second: V
                }
                """);
        ClassDeclNode pair = (ClassDeclNode) unit.declarations().get(0);
        assertThat(pair.typeParameters().size()).isEqualTo(2);
        assertThat(pair.typeParameters().get(0).name()).isEqualTo("K");
        assertThat(pair.typeParameters().get(1).name()).isEqualTo("V");
    }

    @Test
    public void functionTypeParameterListIsRecorded() {
        CompilationUnitNode unit = parseOk("g.sol", """
                func identity<T>(x: T): T {
                    return x
                }
                """);
        FunctionDeclNode identity = (FunctionDeclNode) unit.declarations().get(0);
        assertThat(identity.typeParameters().size()).isEqualTo(1);
        assertThat(identity.typeParameters().get(0).name()).isEqualTo("T");
        assertThat(identity.parameters().get(0).type().name()).isEqualTo("T");
        assertThat(identity.returnType().name()).isEqualTo("T");
    }

    @Test
    public void interfaceAndSignatureTypeParametersAreRecorded() {
        CompilationUnitNode unit = parseOk("g.sol", """
                interface Container<T> {
                    func get(): T
                    func replace<U>(value: U): U {
                        return value
                    }
                }
                """);
        InterfaceDeclNode container = (InterfaceDeclNode) unit.declarations().get(0);
        assertThat(container.typeParameters().size()).isEqualTo(1);
        SignatureDeclNode get = container.signatures().get(0);
        assertThat(get.returnType().name()).isEqualTo("T");
        FunctionDeclNode replace = container.defaultMethods().get(0);
        assertThat(replace.typeParameters().size()).isEqualTo(1);
        assertThat(replace.typeParameters().get(0).name()).isEqualTo("U");
    }

    @Test
    public void methodTypeParameterListIsRecorded() {
        CompilationUnitNode unit = parseOk("g.sol", """
                class Box<T> {
                    func replaceWith<U>(value: U): U {
                        return value
                    }
                }
                """);
        ClassDeclNode box = (ClassDeclNode) unit.declarations().get(0);
        FunctionDeclNode method = box.methods().get(0);
        assertThat(method.typeParameters().size()).isEqualTo(1);
        assertThat(method.typeParameters().get(0).name()).isEqualTo("U");
    }

    @Test
    public void typeApplicationsCarryTheirArguments() {
        CompilationUnitNode unit = parseOk("g.sol", """
                func f(xs: List<String>): Box<Integer> {
                    return Box(1)
                }
                """);
        FunctionDeclNode f = (FunctionDeclNode) unit.declarations().get(0);
        TypeRefNode parameter = f.parameters().get(0).type();
        assertThat(parameter.name()).isEqualTo("List");
        assertThat(parameter.arguments().size()).isEqualTo(1);
        assertThat(parameter.arguments().get(0).name()).isEqualTo("String");
        assertThat(parameter.isNullable()).isFalse();

        TypeRefNode returnType = f.returnType();
        assertThat(returnType.name()).isEqualTo("Box");
        assertThat(returnType.arguments().size()).isEqualTo(1);
        assertThat(returnType.arguments().get(0).name()).isEqualTo("Integer");
    }

    @Test
    public void nestedTypeApplicationsNestStructurally() {
        CompilationUnitNode unit = parseOk("g.sol", """
                func f(xs: List<Box<String>>): Unit {
                }
                """);
        FunctionDeclNode f = (FunctionDeclNode) unit.declarations().get(0);
        TypeRefNode list = f.parameters().get(0).type();
        assertThat(list.name()).isEqualTo("List");
        TypeRefNode box = list.arguments().get(0);
        assertThat(box.name()).isEqualTo("Box");
        assertThat(box.arguments().get(0).name()).isEqualTo("String");
    }

    @Test
    public void nullableMarkerCombinesWithTypeApplications() {
        CompilationUnitNode unit = parseOk("g.sol", """
                func outer(values: List<String>?): Unit {
                }
                func inner(values: List<String?>): Unit {
                }
                """);
        TypeRefNode outer = ((FunctionDeclNode) unit.declarations().get(0)).parameters().get(0).type();
        assertThat(outer.isNullable()).isTrue();
        assertThat(outer.arguments().get(0).isNullable()).isFalse();

        TypeRefNode inner = ((FunctionDeclNode) unit.declarations().get(1)).parameters().get(0).type();
        assertThat(inner.isNullable()).isFalse();
        assertThat(inner.arguments().get(0).isNullable()).isTrue();
    }

    @Test
    public void typeArgumentsInExtendsAndImplementsAreRecorded() {
        CompilationUnitNode unit = parseOk("g.sol", """
                interface Repository<T> {
                    func find(id: Integer): T
                }
                class UserService implements Repository<String> {
                    func find(id: Integer): String {
                        return "x"
                    }
                }
                """);
        InterfaceDeclNode repository = (InterfaceDeclNode) unit.declarations().get(0);
        ClassDeclNode service = (ClassDeclNode) unit.declarations().get(1);
        assertThat(repository.typeParameters().size()).isEqualTo(1);
        TypeRefNode implemented = service.interfaces().get(0);
        assertThat(implemented.name()).isEqualTo("Repository");
        assertThat(implemented.arguments().get(0).name()).isEqualTo("String");
    }

    @Test
    public void aTypeParameterListIsNotAComparisonExpression() {
        CompilationUnitNode unit = parseOk("g.sol", """
                func lessThan(a: Integer, b: Integer): Boolean {
                    return a < b
                }
                """);
        FunctionDeclNode lessThan = (FunctionDeclNode) unit.declarations().get(0);
        assertThat(lessThan.typeParameters().isEmpty()).isTrue();
        assertThat(lessThan.parameters().size()).isEqualTo(2);
    }

    @Test
    public void emptyTypeParameterListIsRejected() {
        assertThat(parseFails("g.sol", """
                class Box<> {
                }
                """).hasErrors()).isTrue();
    }

    @Test
    public void emptyTypeArgumentListIsRejected() {
        assertThat(parseFails("g.sol", """
                func f(xs: List<>) {
                }
                """).hasErrors()).isTrue();
    }

    @Test
    public void trailingTypeArgumentCommaIsRejected() {
        assertThat(parseFails("g.sol", """
                func f(xs: List<String,>) {
                }
                """).hasErrors()).isTrue();
    }
}
