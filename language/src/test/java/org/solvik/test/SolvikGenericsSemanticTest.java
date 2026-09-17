/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.util.List;
import org.junit.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.AnyType;
import org.solvik.type.ClassType;
import org.solvik.type.IntType;
import org.solvik.type.ListType;
import org.solvik.type.ObjectType;
import org.solvik.type.ParameterizedType;
import org.solvik.type.StringType;
import org.solvik.type.Type;
import org.solvik.type.TypeParameterType;

/**
 * Positive Phase 11 semantic tests (docs/LANGUAGE_SPEC.md section 11): generic declarations,
 * applications, substitution, inference, invariance, and the built-in immutable {@code List<T>}.
 */
public final class SolvikGenericsSemanticTest {

    private static final String GENERIC_BOX = """
            class Box<T> {
                var value: T

                init(value: T) {
                    this.value = value
                }

                func get(): T {
                    return this.value
                }

                func replaceWith<U>(value: U): U {
                    return value
                }
            }
            """;

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("generics.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertTrue("analysis must succeed: " + result.diagnostics().all(), result.isSuccess());
        return result.requireProgram();
    }

    private static Type typeOfReturn(CheckedProgram program, int functionIndex, int statementIndex) {
        FunctionDeclNode function = (FunctionDeclNode) program.unit().declarations().get(functionIndex);
        ReturnStmtNode statement = (ReturnStmtNode) function.body().statements().get(statementIndex);
        return program.typeOf(statement.value().orElseThrow()).orElseThrow();
    }

    @Test
    public void genericClassHasTypeParametersAndApplicationIsCanonical() {
        CheckedProgram program = check(GENERIC_BOX + """
                func main(): Unit {
                    val box = Box(5)
                }
                """);
        ClassType box = (ClassType) program.classSymbol("Box").orElseThrow().type();
        assertEquals(1, box.typeParameters().size());
        TypeParameterType parameter = box.typeParameters().get(0);
        assertEquals("T", parameter.name());

        Type applied = box.parameterizedView(List.of(IntType.INSTANCE));
        assertTrue(applied instanceof ParameterizedType);
        assertEquals("Box<Int>", applied.name());
        assertEquals(IntType.INSTANCE, ((ParameterizedType) applied).arguments().get(0));
        assertSame(applied, box.parameterizedView(List.of(IntType.INSTANCE)));
    }

    @Test
    public void constructionInfersTheTypeArgumentFromTheConstructorArgument() {
        CheckedProgram program = check(GENERIC_BOX + """
                func main(): Unit {
                    val intBox = Box(5)
                }
                """);
        FunctionDeclNode main = (FunctionDeclNode) program.unit().declarations().get(1);
        LocalDeclNode declaration = (LocalDeclNode) main.body().statements().get(0);
        Type inferred = program.typeOf(declaration.initializer()).orElseThrow();
        assertTrue(inferred instanceof ParameterizedType);
        assertEquals("Box<Int>", inferred.name());
    }

    @Test
    public void memberReadsAndCallsSubstituteTheReceiverTypeArgument() {
        CheckedProgram program = check(GENERIC_BOX + """
                func read(box: Box<Int>): Int {
                    return box.value
                }
                func get(box: Box<String>): String {
                    return box.get()
                }
                """);
        assertEquals(IntType.INSTANCE, typeOfReturn(program, 1, 0));
        assertEquals(StringType.INSTANCE, typeOfReturn(program, 2, 0));
    }

    @Test
    public void genericFunctionSubstitutesItsReturnType() {
        CheckedProgram program = check("""
                func identity<T>(x: T): T {
                    return x
                }
                func useInt(): Int {
                    return identity(5)
                }
                func useString(): String {
                    return identity("hi")
                }
                """);
        assertEquals(IntType.INSTANCE, typeOfReturn(program, 1, 0));
        assertEquals(StringType.INSTANCE, typeOfReturn(program, 2, 0));
    }

    @Test
    public void genericMethodSubstitutesItsOwnTypeParameter() {
        CheckedProgram program = check(GENERIC_BOX + """
                func use(box: Box<Int>): String {
                    return box.replaceWith("x")
                }
                """);
        assertEquals(StringType.INSTANCE, typeOfReturn(program, 1, 0));
    }

    @Test
    public void genericApplicationsAreInvariant() {
        CheckedProgram program = check(GENERIC_BOX + """
                func exact(): Box<String> {
                    return Box("x")
                }
                """);
        ClassType box = (ClassType) program.classSymbol("Box").orElseThrow().type();
        Type stringBox = box.parameterizedView(List.of(StringType.INSTANCE));
        Type intBox = box.parameterizedView(List.of(IntType.INSTANCE));
        Type anyBox = box.parameterizedView(List.of(AnyType.INSTANCE));
        assertTrue(stringBox.isAssignableTo(stringBox));
        assertFalse(stringBox.isAssignableTo(intBox));
        assertFalse(intBox.isAssignableTo(stringBox));
        assertFalse("type arguments are invariant, not covariant", stringBox.isAssignableTo(anyBox));
        assertFalse(anyBox.isAssignableTo(stringBox));
        assertTrue(stringBox.isAssignableTo(ObjectType.INSTANCE));
    }

    @Test
    public void nestedGenericApplicationsAreCanonicalAndTyped() {
        CheckedProgram program = check("""
                class Box<T> {
                    var value: T

                    init(value: T) {
                        this.value = value
                    }
                }
                func nested(box: Box<List<Box<String>>>): Box<String> {
                    return box.value.get(0)
                }
                """);
        ClassType box = (ClassType) program.classSymbol("Box").orElseThrow().type();
        Type inner = box.parameterizedView(List.of(StringType.INSTANCE));
        Type middle = ListType.INSTANCE.parameterizedView(List.of(inner));
        Type outer = box.parameterizedView(List.of(middle));
        assertEquals("Box<List<Box<String>>>", outer.name());
        assertEquals(inner, typeOfReturn(program, 1, 0));
    }

    @Test
    public void listMemberTypesAreChecked() {
        CheckedProgram program = check("""
                func size(values: List<String>): Int {
                    return values.size
                }
                func first(values: List<Int>): Int {
                    return values.get(0)
                }
                func nested(values: List<List<String>>): String {
                    return values.get(0).get(0)
                }
                """);
        assertEquals(IntType.INSTANCE, typeOfReturn(program, 0, 0));
        assertEquals(IntType.INSTANCE, typeOfReturn(program, 1, 0));
        assertEquals(StringType.INSTANCE, typeOfReturn(program, 2, 0));
    }

    @Test
    public void listTypesAreInvariantAndUnderObject() {
        Type stringList = ListType.INSTANCE.parameterizedView(List.of(StringType.INSTANCE));
        Type intList = ListType.INSTANCE.parameterizedView(List.of(IntType.INSTANCE));
        assertTrue(stringList.isAssignableTo(stringList));
        assertFalse(stringList.isAssignableTo(intList));
        assertTrue(stringList.isAssignableTo(ObjectType.INSTANCE));
        assertTrue(stringList.isAssignableTo(AnyType.INSTANCE));
    }

    @Test
    public void genericInterfaceConformanceSubstitutesTypeArguments() {
        CheckedProgram program = check("""
                interface Container<T> {
                    func get(): T
                }
                class StringBox implements Container<String> {
                    func get(): String {
                        return "x"
                    }
                }
                func throughInterface(container: Container<String>): String {
                    return container.get()
                }
                func throughClass(box: StringBox): String {
                    return box.get()
                }
                """);
        assertEquals(StringType.INSTANCE, typeOfReturn(program, 2, 0));
        assertEquals(StringType.INSTANCE, typeOfReturn(program, 3, 0));
    }

    @Test
    public void genericClassImplementsMatchingGenericInterface() {
        CheckedProgram program = check("""
                interface Container<T> {
                    func get(): T
                }
                class Holder<T> implements Container<T> {
                    var value: T

                    init(value: T) {
                        this.value = value
                    }

                    func get(): T {
                        return this.value
                    }
                }
                func use(holder: Holder<String>): String {
                    return holder.get()
                }
                """);
        assertEquals(StringType.INSTANCE, typeOfReturn(program, 2, 0));
    }

    @Test
    public void functionTypeOfAGenericCallableStillCarriesItsParameterTypes() {
        CheckedProgram program = check("""
                func identity<T>(x: T): T {
                    return x
                }
                """);
        var identity = program.function("identity").orElseThrow();
        assertEquals(1, identity.typeParameters().size());
        assertEquals("(T) -> T", identity.functionType().name());
    }

    @Test
    public void inheritedGenericMembersSubstituteThroughTheSupertype() {
        CheckedProgram program = check("""
                open class Box<T> {
                    var value: T

                    init(value: T) {
                        this.value = value
                    }

                    open func get(): T {
                        return this.value
                    }
                }
                class IntBox extends Box<Int> {
                    init(value: Int) {
                        super(value)
                    }
                }
                func use(box: IntBox): Int {
                    return box.get()
                }
                func prop(box: IntBox): Int {
                    return box.value
                }
                """);
        assertEquals(IntType.INSTANCE, typeOfReturn(program, 2, 0));
        assertEquals(IntType.INSTANCE, typeOfReturn(program, 3, 0));
    }

    @Test
    public void genericSubclassSubstitutesThroughAGenericSupertype() {
        CheckedProgram program = check("""
                open class Box<T> {
                    var value: T

                    init(value: T) {
                        this.value = value
                    }
                }
                class Wrapper<U> extends Box<U> {
                    init(value: U) {
                        super(value)
                    }
                }
                func use(wrapper: Wrapper<String>): String {
                    return wrapper.value
                }
                """);
        assertEquals(StringType.INSTANCE, typeOfReturn(program, 2, 0));
    }

    @Test
    public void genericApplicationsRemainAssignableToAny() {
        CheckedProgram program = check("""
                class Box<T> {
                    var value: T

                    init(value: T) {
                        this.value = value
                    }
                }
                func acceptAny(value: Any): Unit {
                }
                func use(box: Box<String>): Unit {
                    acceptAny(box)
                }
                """);
        assertFalse(program.classes().isEmpty());
    }
}
