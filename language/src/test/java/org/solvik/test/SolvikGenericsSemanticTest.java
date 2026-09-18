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

import java.util.List;
import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.ast.declaration.FunctionDeclNode;
import org.solvik.ast.statement.LocalDeclNode;
import org.solvik.ast.statement.ReturnStmtNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.AnyType;
import org.solvik.type.BuiltinCollectionTypes;
import org.solvik.type.ClassType;
import org.solvik.type.IntType;
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

                Box(value: T) {
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
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
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
                    val box = Box(5)
                """);
        ClassType box = (ClassType) program.classSymbol("Box").orElseThrow().type();
        assertThat(box.typeParameters().size()).isEqualTo(1);
        TypeParameterType parameter = box.typeParameters().get(0);
        assertThat(parameter.name()).isEqualTo("T");

        Type applied = box.parameterizedView(List.of(IntType.INSTANCE));
        assertThat(applied instanceof ParameterizedType).isTrue();
        assertThat(applied.name()).isEqualTo("Box<Int>");
        assertThat(((ParameterizedType) applied).arguments().get(0)).isEqualTo(IntType.INSTANCE);
        assertThat(box.parameterizedView(List.of(IntType.INSTANCE))).isSameAs(applied);
    }

    @Test
    public void constructionInfersTheTypeArgumentFromTheConstructorArgument() {
        CheckedProgram program = check(GENERIC_BOX + """
                    val intBox = Box(5)
                """);
        LocalDeclNode declaration = (LocalDeclNode) program.unit().statements().get(0);
        Type inferred = program.typeOf(declaration.initializer()).orElseThrow();
        assertThat(inferred instanceof ParameterizedType).isTrue();
        assertThat(inferred.name()).isEqualTo("Box<Int>");
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
        assertThat(typeOfReturn(program, 1, 0)).isEqualTo(IntType.INSTANCE);
        assertThat(typeOfReturn(program, 2, 0)).isEqualTo(StringType.INSTANCE);
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
        assertThat(typeOfReturn(program, 1, 0)).isEqualTo(IntType.INSTANCE);
        assertThat(typeOfReturn(program, 2, 0)).isEqualTo(StringType.INSTANCE);
    }

    @Test
    public void genericMethodSubstitutesItsOwnTypeParameter() {
        CheckedProgram program = check(GENERIC_BOX + """
                func use(box: Box<Int>): String {
                    return box.replaceWith("x")
                }
                """);
        assertThat(typeOfReturn(program, 1, 0)).isEqualTo(StringType.INSTANCE);
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
        assertThat(stringBox.isAssignableTo(stringBox)).isTrue();
        assertThat(stringBox.isAssignableTo(intBox)).isFalse();
        assertThat(intBox.isAssignableTo(stringBox)).isFalse();
        assertThat(stringBox.isAssignableTo(anyBox)).as("type arguments are invariant, not covariant").isFalse();
        assertThat(anyBox.isAssignableTo(stringBox)).isFalse();
        assertThat(stringBox.isAssignableTo(AnyType.INSTANCE)).isTrue();
    }

    @Test
    public void nestedGenericApplicationsAreCanonicalAndTyped() {
        CheckedProgram program = check("""
                class Box<T> {
                    var value: T

                    Box(value: T) {
                        this.value = value
                    }
                }
                func nested(box: Box<List<Box<String>>>): Box<String> {
                    return box.value.get(0)
                }
                """);
        ClassType box = (ClassType) program.classSymbol("Box").orElseThrow().type();
        Type inner = box.parameterizedView(List.of(StringType.INSTANCE));
        Type middle = BuiltinCollectionTypes.LIST.parameterizedView(List.of(inner));
        Type outer = box.parameterizedView(List.of(middle));
        assertThat(outer.name()).isEqualTo("Box<List<Box<String>>>");
        assertThat(typeOfReturn(program, 1, 0)).isEqualTo(inner);
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
        assertThat(typeOfReturn(program, 0, 0)).isEqualTo(IntType.INSTANCE);
        assertThat(typeOfReturn(program, 1, 0)).isEqualTo(IntType.INSTANCE);
        assertThat(typeOfReturn(program, 2, 0)).isEqualTo(StringType.INSTANCE);
    }

    @Test
    public void listTypesAreInvariantAndUnderAny() {
        Type stringList = BuiltinCollectionTypes.LIST.parameterizedView(List.of(StringType.INSTANCE));
        Type intList = BuiltinCollectionTypes.LIST.parameterizedView(List.of(IntType.INSTANCE));
        assertThat(stringList.isAssignableTo(stringList)).isTrue();
        assertThat(stringList.isAssignableTo(intList)).isFalse();
        assertThat(stringList.isAssignableTo(AnyType.INSTANCE)).isTrue();
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
        assertThat(typeOfReturn(program, 2, 0)).isEqualTo(StringType.INSTANCE);
        assertThat(typeOfReturn(program, 3, 0)).isEqualTo(StringType.INSTANCE);
    }

    @Test
    public void genericClassImplementsMatchingGenericInterface() {
        CheckedProgram program = check("""
                interface Container<T> {
                    func get(): T
                }
                class Holder<T> implements Container<T> {
                    var value: T

                    Holder(value: T) {
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
        assertThat(typeOfReturn(program, 2, 0)).isEqualTo(StringType.INSTANCE);
    }

    @Test
    public void functionTypeOfAGenericCallableStillCarriesItsParameterTypes() {
        CheckedProgram program = check("""
                func identity<T>(x: T): T {
                    return x
                }
                """);
        var identity = program.function("identity").orElseThrow();
        assertThat(identity.typeParameters().size()).isEqualTo(1);
        assertThat(identity.functionType().name()).isEqualTo("(T) -> T");
    }

    @Test
    public void inheritedGenericMembersSubstituteThroughTheSupertype() {
        CheckedProgram program = check("""
                open class Box<T> {
                    var value: T

                    Box(value: T) {
                        this.value = value
                    }

                    open func get(): T {
                        return this.value
                    }
                }
                class IntBox extends Box<Int> {
                    IntBox(value: Int) {
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
        assertThat(typeOfReturn(program, 2, 0)).isEqualTo(IntType.INSTANCE);
        assertThat(typeOfReturn(program, 3, 0)).isEqualTo(IntType.INSTANCE);
    }

    @Test
    public void genericSubclassSubstitutesThroughAGenericSupertype() {
        CheckedProgram program = check("""
                open class Box<T> {
                    var value: T

                    Box(value: T) {
                        this.value = value
                    }
                }
                class Wrapper<U> extends Box<U> {
                    Wrapper(value: U) {
                        super(value)
                    }
                }
                func use(wrapper: Wrapper<String>): String {
                    return wrapper.value
                }
                """);
        assertThat(typeOfReturn(program, 2, 0)).isEqualTo(StringType.INSTANCE);
    }

    @Test
    public void genericApplicationsRemainAssignableToAny() {
        CheckedProgram program = check("""
                class Box<T> {
                    var value: T

                    Box(value: T) {
                        this.value = value
                    }
                }
                func acceptAny(value: Any): Unit {
                }
                func use(box: Box<String>): Unit {
                    acceptAny(box)
                }
                """);
        assertThat(program.classes().isEmpty()).isFalse();
    }

    @Test
    public void unboundedTypeParameterIsReturnableAndAssignableAsAny() {
        CheckedProgram program = check("""
                func widen<T>(value: T): Any {
                    return value
                }
                func store<T>(value: T): Unit {
                    val stored: Any = value
                }
                """);
        TypeParameterType parameter = program.function("widen").orElseThrow().typeParameters().get(0);
        assertThat(parameter.isSubtypeOf(AnyType.INSTANCE)).as("an unconstrained T is below Any").isTrue();
        assertThat(parameter.isSubtypeOf(StringType.INSTANCE)).isFalse();
    }
}
