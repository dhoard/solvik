/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
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
        assertEquals(1, box.typeParameters().size());
        assertEquals("T", box.typeParameters().get(0).name());
        PropertyDeclNode property = box.properties().get(0);
        assertEquals("value", property.name());
        assertEquals("T", property.declaredType().orElseThrow().name());
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
        assertEquals(2, pair.typeParameters().size());
        assertEquals("K", pair.typeParameters().get(0).name());
        assertEquals("V", pair.typeParameters().get(1).name());
    }

    @Test
    public void functionTypeParameterListIsRecorded() {
        CompilationUnitNode unit = parseOk("g.sol", """
                func identity<T>(x: T): T {
                    return x
                }
                """);
        FunctionDeclNode identity = (FunctionDeclNode) unit.declarations().get(0);
        assertEquals(1, identity.typeParameters().size());
        assertEquals("T", identity.typeParameters().get(0).name());
        assertEquals("T", identity.parameters().get(0).type().name());
        assertEquals("T", identity.returnType().name());
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
        assertEquals(1, container.typeParameters().size());
        SignatureDeclNode get = container.signatures().get(0);
        assertEquals("T", get.returnType().name());
        FunctionDeclNode replace = container.defaultMethods().get(0);
        assertEquals(1, replace.typeParameters().size());
        assertEquals("U", replace.typeParameters().get(0).name());
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
        assertEquals(1, method.typeParameters().size());
        assertEquals("U", method.typeParameters().get(0).name());
    }

    @Test
    public void typeApplicationsCarryTheirArguments() {
        CompilationUnitNode unit = parseOk("g.sol", """
                func f(xs: List<String>): Box<Int> {
                    return Box(1)
                }
                """);
        FunctionDeclNode f = (FunctionDeclNode) unit.declarations().get(0);
        TypeRefNode parameter = f.parameters().get(0).type();
        assertEquals("List", parameter.name());
        assertEquals(1, parameter.arguments().size());
        assertEquals("String", parameter.arguments().get(0).name());
        assertFalse(parameter.isNullable());

        TypeRefNode returnType = f.returnType();
        assertEquals("Box", returnType.name());
        assertEquals(1, returnType.arguments().size());
        assertEquals("Int", returnType.arguments().get(0).name());
    }

    @Test
    public void nestedTypeApplicationsNestStructurally() {
        CompilationUnitNode unit = parseOk("g.sol", """
                func f(xs: List<Box<String>>): Unit {
                }
                """);
        FunctionDeclNode f = (FunctionDeclNode) unit.declarations().get(0);
        TypeRefNode list = f.parameters().get(0).type();
        assertEquals("List", list.name());
        TypeRefNode box = list.arguments().get(0);
        assertEquals("Box", box.name());
        assertEquals("String", box.arguments().get(0).name());
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
        assertTrue(outer.isNullable());
        assertFalse(outer.arguments().get(0).isNullable());

        TypeRefNode inner = ((FunctionDeclNode) unit.declarations().get(1)).parameters().get(0).type();
        assertFalse(inner.isNullable());
        assertTrue(inner.arguments().get(0).isNullable());
    }

    @Test
    public void typeArgumentsInExtendsAndImplementsAreRecorded() {
        CompilationUnitNode unit = parseOk("g.sol", """
                interface Repository<T> {
                    func find(id: Int): T
                }
                class UserService implements Repository<String> {
                    func find(id: Int): String {
                        return "x"
                    }
                }
                """);
        InterfaceDeclNode repository = (InterfaceDeclNode) unit.declarations().get(0);
        ClassDeclNode service = (ClassDeclNode) unit.declarations().get(1);
        assertEquals(1, repository.typeParameters().size());
        TypeRefNode implemented = service.interfaces().get(0);
        assertEquals("Repository", implemented.name());
        assertEquals("String", implemented.arguments().get(0).name());
    }

    @Test
    public void aTypeParameterListIsNotAComparisonExpression() {
        CompilationUnitNode unit = parseOk("g.sol", """
                func lessThan(a: Int, b: Int): Boolean {
                    return a < b
                }
                """);
        FunctionDeclNode lessThan = (FunctionDeclNode) unit.declarations().get(0);
        assertTrue(lessThan.typeParameters().isEmpty());
        assertEquals(2, lessThan.parameters().size());
    }

    @Test
    public void emptyTypeParameterListIsRejected() {
        assertTrue(parseFails("g.sol", """
                class Box<> {
                }
                """).hasErrors());
    }

    @Test
    public void emptyTypeArgumentListIsRejected() {
        assertTrue(parseFails("g.sol", """
                func f(xs: List<>) {
                }
                """).hasErrors());
    }

    @Test
    public void trailingTypeArgumentCommaIsRejected() {
        assertTrue(parseFails("g.sol", """
                func f(xs: List<String,>) {
                }
                """).hasErrors());
    }
}
