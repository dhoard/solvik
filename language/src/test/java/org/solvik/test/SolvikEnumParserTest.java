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

import org.junit.Test;
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
                    Ok(Int)
                    Error(String)
                }
                """);
        assertEquals(1, unit.declarations().size());
        EnumDeclNode result = (EnumDeclNode) unit.declarations().get(0);
        assertEquals("Result", result.name());
        assertEquals(2, result.variants().size());
        EnumVariantNode ok = result.variants().get(0);
        assertEquals("Ok", ok.name());
        assertEquals(1, ok.valueTypes().size());
        assertEquals("Int", ok.valueTypes().get(0).name());
        EnumVariantNode error = result.variants().get(1);
        assertEquals("Error", error.name());
        assertEquals("String", error.valueTypes().get(0).name());
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
        assertEquals(3, color.variants().size());
        assertTrue(color.variants().get(0).valueTypes().isEmpty());
        assertEquals("Green", color.variants().get(1).name());
        assertEquals("Blue", color.variants().get(2).name());
    }

    @Test
    public void aVariantMayCarrySeveralValues() {
        CompilationUnitNode unit = parseOk("e.sol", """
                enum Shape {
                    Rectangle(Int, Int)
                }
                """);
        EnumDeclNode shape = (EnumDeclNode) unit.declarations().get(0);
        assertEquals(2, shape.variants().get(0).valueTypes().size());
        assertEquals("Int", shape.variants().get(0).valueTypes().get(1).name());
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
        assertEquals(1, option.typeParameters().size());
        assertEquals("T", option.typeParameters().get(0).name());
        assertEquals("T", option.variants().get(0).valueTypes().get(0).name());
        assertEquals("None", option.variants().get(1).name());
    }

    @Test
    public void variantValueTypesMayBeGenericApplications() {
        CompilationUnitNode unit = parseOk("e.sol", """
                enum Payload {
                    Items(List<String>)
                }
                """);
        EnumDeclNode payload = (EnumDeclNode) unit.declarations().get(0);
        assertEquals("List", payload.variants().get(0).valueTypes().get(0).name());
        assertEquals(1, payload.variants().get(0).valueTypes().get(0).arguments().size());
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
        assertTrue(((ClassDeclNode) unit.declarations().get(0)).isSealed());
        assertFalse(((ClassDeclNode) unit.declarations().get(0)).isOpen());
        assertTrue(((ClassDeclNode) unit.declarations().get(1)).isOpen());
        assertFalse(((ClassDeclNode) unit.declarations().get(1)).isSealed());
        assertFalse(((ClassDeclNode) unit.declarations().get(2)).isSealed());
        assertFalse(((ClassDeclNode) unit.declarations().get(2)).isOpen());
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
        assertEquals(3, unit.declarations().size());
        assertEquals("First", ((ClassDeclNode) unit.declarations().get(0)).name());
        assertEquals("Second", ((EnumDeclNode) unit.declarations().get(1)).name());
        assertEquals("Third", ((ClassDeclNode) unit.declarations().get(2)).name());
    }

    @Test
    public void aFunctionInsideAnEnumBodyIsRejected() {
        assertTrue(parseFails("e.sol", """
                enum Result {
                    Ok(Int)
                    func broken(): Int {
                        return 1
                    }
                }
                """).hasErrors());
    }

    @Test
    public void anEnumWithoutABodyIsRejected() {
        assertTrue(parseFails("e.sol", """
                enum Result
                """).hasErrors());
    }

    @Test
    public void anEnumWithoutANameIsRejected() {
        assertTrue(parseFails("e.sol", """
                enum {
                    Ok(Int)
                }
                """).hasErrors());
    }

    @Test
    public void aVariantValueListWithATrailingCommaIsRejected() {
        assertTrue(parseFails("e.sol", """
                enum Result {
                    Ok(Int,)
                    Error(String)
                }
                """).hasErrors());
    }

    @Test
    public void sealedIsRejectedOnAFunction() {
        assertTrue(parseFails("e.sol", """
                sealed func f(): Unit {
                }
                """).hasErrors());
    }

    @Test
    public void anEnumMayNotExtendOrImplement() {
        assertTrue(parseFails("e.sol", """
                enum Result extends Base {
                }
                """).hasErrors());
    }
}
