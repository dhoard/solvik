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
import org.solvik.ast.declaration.EnumDeclNode;
import org.solvik.ast.declaration.EnumVariantNode;

/**
 * Phase 12 parser tests (docs/LANGUAGE_SPEC.md section 12): enum declarations with value-carrying
 * variants and the {@code sealed} class modifier.
 */
public final class SolvikEnumParserTest {

    @Test
    public void enumDeclarationRecordsItsValueCarryingVariants() {
        CompilationUnitNode unit = parseOk("e.sol", """
                enum Result {
                    Ok(Integer)
                    Error(String)
                }
                """);
        assertThat(unit.declarations().size()).isEqualTo(1);
        EnumDeclNode result = (EnumDeclNode) unit.declarations().get(0);
        assertThat(result.name()).isEqualTo("Result");
        assertThat(result.variants().size()).isEqualTo(2);
        EnumVariantNode ok = result.variants().get(0);
        assertThat(ok.name()).isEqualTo("Ok");
        assertThat(ok.valueTypes().size()).isEqualTo(1);
        assertThat(ok.valueTypes().get(0).name()).isEqualTo("Integer");
        EnumVariantNode error = result.variants().get(1);
        assertThat(error.name()).isEqualTo("Error");
        assertThat(error.valueTypes().get(0).name()).isEqualTo("String");
    }

    @Test
    public void valueLessVariantsAreRecorded() {
        CompilationUnitNode unit = parseOk("e.sol", """
                enum Color {
                    Red
                    Green
                    Blue
                }
                """);
        EnumDeclNode color = (EnumDeclNode) unit.declarations().get(0);
        assertThat(color.variants().size()).isEqualTo(3);
        assertThat(color.variants().get(0).valueTypes().isEmpty()).isTrue();
        assertThat(color.variants().get(1).name()).isEqualTo("Green");
        assertThat(color.variants().get(2).name()).isEqualTo("Blue");
    }

    @Test
    public void aVariantMayCarrySeveralValues() {
        CompilationUnitNode unit = parseOk("e.sol", """
                enum Shape {
                    Rectangle(Integer, Integer)
                }
                """);
        EnumDeclNode shape = (EnumDeclNode) unit.declarations().get(0);
        assertThat(shape.variants().get(0).valueTypes().size()).isEqualTo(2);
        assertThat(shape.variants().get(0).valueTypes().get(1).name()).isEqualTo("Integer");
    }

    @Test
    public void aGenericEnumRecordsItsTypeParameters() {
        CompilationUnitNode unit = parseOk("e.sol", """
                enum Option<T> {
                    Some(T)
                    None
                }
                """);
        EnumDeclNode option = (EnumDeclNode) unit.declarations().get(0);
        assertThat(option.typeParameters().size()).isEqualTo(1);
        assertThat(option.typeParameters().get(0).name()).isEqualTo("T");
        assertThat(option.variants().get(0).valueTypes().get(0).name()).isEqualTo("T");
        assertThat(option.variants().get(1).name()).isEqualTo("None");
    }

    @Test
    public void variantValueTypesMayBeGenericApplications() {
        CompilationUnitNode unit = parseOk("e.sol", """
                enum Payload {
                    Items(List<String>)
                }
                """);
        EnumDeclNode payload = (EnumDeclNode) unit.declarations().get(0);
        assertThat(payload.variants().get(0).valueTypes().get(0).name()).isEqualTo("List");
        assertThat(payload.variants().get(0).valueTypes().get(0).arguments().size()).isEqualTo(1);
    }

    @Test
    public void sealedAndOpenClassModifiersAreRecorded() {
        CompilationUnitNode unit = parseOk("e.sol", """
                sealed class Shape {
                }
                open class Base {
                }
                class Plain {
                }
                """);
        assertThat(((ClassDeclNode) unit.declarations().get(0)).isSealed()).isTrue();
        assertThat(((ClassDeclNode) unit.declarations().get(0)).isOpen()).isFalse();
        assertThat(((ClassDeclNode) unit.declarations().get(1)).isOpen()).isTrue();
        assertThat(((ClassDeclNode) unit.declarations().get(1)).isSealed()).isFalse();
        assertThat(((ClassDeclNode) unit.declarations().get(2)).isSealed()).isFalse();
        assertThat(((ClassDeclNode) unit.declarations().get(2)).isOpen()).isFalse();
    }

    @Test
    public void enumsAndClassesKeepSourceDeclarationOrder() {
        CompilationUnitNode unit = parseOk("e.sol", """
                class First {
                }
                enum Second {
                    A
                }
                class Third {
                }
                """);
        assertThat(unit.declarations().size()).isEqualTo(3);
        assertThat(((ClassDeclNode) unit.declarations().get(0)).name()).isEqualTo("First");
        assertThat(((EnumDeclNode) unit.declarations().get(1)).name()).isEqualTo("Second");
        assertThat(((ClassDeclNode) unit.declarations().get(2)).name()).isEqualTo("Third");
    }

    @Test
    public void aFunctionInsideAnEnumBodyIsRejected() {
        assertThat(parseFails("e.sol", """
                enum Result {
                    Ok(Integer)
                    func broken(): Integer {
                        return 1
                    }
                }
                """).hasErrors()).isTrue();
    }

    @Test
    public void anEnumWithoutABodyIsRejected() {
        assertThat(parseFails("e.sol", """
                enum Result
                """).hasErrors()).isTrue();
    }

    @Test
    public void anEnumWithoutANameIsRejected() {
        assertThat(parseFails("e.sol", """
                enum {
                    Ok(Integer)
                }
                """).hasErrors()).isTrue();
    }

    @Test
    public void aVariantValueListWithATrailingCommaIsRejected() {
        assertThat(parseFails("e.sol", """
                enum Result {
                    Ok(Integer,)
                    Error(String)
                }
                """).hasErrors()).isTrue();
    }

    @Test
    public void sealedIsRejectedOnAFunction() {
        assertThat(parseFails("e.sol", """
                sealed func f(): Unit {
                }
                """).hasErrors()).isTrue();
    }

    @Test
    public void anEnumMayNotExtendOrImplement() {
        assertThat(parseFails("e.sol", """
                enum Result extends Base {
                }
                """).hasErrors()).isTrue();
    }
}
