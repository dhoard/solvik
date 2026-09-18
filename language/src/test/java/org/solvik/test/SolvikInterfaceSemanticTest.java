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
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.semantic.CheckedProgram;
import org.solvik.semantic.ClassSymbol;
import org.solvik.semantic.FunctionSymbol;
import org.solvik.semantic.InterfaceSymbol;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.BooleanType;
import org.solvik.type.IntType;
import org.solvik.type.InterfaceType;
import org.solvik.type.ObjectType;
import org.solvik.type.StringType;
import org.solvik.type.Type;

/**
 * Positive Phase 8 semantic tests: interface descriptors, multiple {@code implements}, default-method
 * typing, inherited requirements, interface-typed assignability, and the resolution recorded for
 * lowering.
 */
public final class SolvikInterfaceSemanticTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("interface.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
        return result.program().orElseThrow();
    }

    @Test
    public void interfaceDescriptorRecordsSignaturesAndDefaults() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String

                    func greeting(): String {
                        return "Hello " .. name()
                    }
                }
                """);
        InterfaceSymbol named = program.interfaceSymbol("Named").orElseThrow();
        assertThat(named.name()).isEqualTo("Named");
        assertThat(named.type() instanceof InterfaceType).isTrue();
        assertThat(named.declaredMembers().stream().map(FunctionSymbol::name).collect(Collectors.toList())).isEqualTo(List.of("name", "greeting"));

        FunctionSymbol name = named.member("name").orElseThrow();
        assertThat(name.isAbstractSignature()).isTrue();
        assertThat(name.hasImplementation()).isFalse();
        assertThat(name.returnType()).isEqualTo(StringType.INSTANCE);

        FunctionSymbol greeting = named.member("greeting").orElseThrow();
        assertThat(greeting.hasImplementation()).isTrue();
        assertThat(greeting.isInterfaceMember()).isTrue();
        assertThat(greeting.interfaceOwner().name()).isEqualTo("Named");
    }

    @Test
    public void oneInterfaceIsSatisfiedByAnImplementingMethod() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String
                }
                class User implements Named {
                    func name(): String {
                        return "Doug"
                    }
                }
                """);
        ClassSymbol user = program.classSymbol("User").orElseThrow();
        assertThat(user.interfaces().stream().map(InterfaceSymbol::name).collect(Collectors.toList())).isEqualTo(List.of("Named"));
        assertThat(user.interfaceImplementation("name").orElseThrow()).isEqualTo(user.declaredMethods().get(0));
        assertThat(user.method("name").isPresent()).isTrue();
    }

    @Test
    public void multipleInterfacesAreConformancedIndependently() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String
                }
                interface Aged {
                    func age(): Int
                }
                class User implements Named, Aged {
                    func name(): String {
                        return "Doug"
                    }

                    func age(): Int {
                        return 42
                    }
                }
                """);
        ClassSymbol user = program.classSymbol("User").orElseThrow();
        assertThat(user.interfaces().stream().map(InterfaceSymbol::name).collect(Collectors.toList())).isEqualTo(List.of("Named", "Aged"));
        assertThat(user.interfaceImplementation("name").orElseThrow().name()).isEqualTo("name");
        assertThat(user.interfaceImplementation("age").orElseThrow().name()).isEqualTo("age");
        assertThat(user.methods().stream().map(FunctionSymbol::name).collect(Collectors.toList())).isEqualTo(List.of("name", "age"));
    }

    @Test
    public void aDefaultMethodSatisfiesItsOwnInterfaceRequirement() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String

                    func greeting(): String {
                        return "Hello " .. name()
                    }
                }
                class User implements Named {
                    func name(): String {
                        return "Doug"
                    }
                }
                """);
        ClassSymbol user = program.classSymbol("User").orElseThrow();
        InterfaceSymbol named = program.interfaceSymbol("Named").orElseThrow();
        // `greeting` is not declared by User: the interface default is its effective implementation.
        assertThat(user.interfaceImplementation("greeting").orElseThrow()).isEqualTo(named.member("greeting").orElseThrow());
        assertThat(user.interfaceImplementation("name").orElseThrow()).isEqualTo(user.declaredMethods().get(0));
    }

    @Test
    public void defaultMethodIsInstalledInTheClassDispatchTable() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String

                    func greeting(): String {
                        return "Hello " .. name()
                    }
                }
                class User implements Named {
                    func name(): String {
                        return "Doug"
                    }
                }
                open class Manager implements Named {
                    func name(): String {
                        return "Manager"
                    }
                }
                """);
        for (String className : List.of("User", "Manager")) {
            ClassSymbol klass = program.classSymbol(className).orElseThrow();
            FunctionSymbol greeting = klass.method("greeting").orElseThrow();
            assertThat(greeting.isInterfaceMember()).isTrue();
            assertThat(greeting.name()).isEqualTo("greeting");
            // The same default symbol is shared by every conforming class, not copied per class.
            assertThat(greeting).isEqualTo(program.interfaceSymbol("Named").orElseThrow().member("greeting").orElseThrow());
        }
    }

    @Test
    public void aDefaultMethodBodyCallsSiblingRequirementsThroughThis() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String

                    func shoutName(): String {
                        return "! " .. name()
                    }
                }
                """);
        InterfaceSymbol named = program.interfaceSymbol("Named").orElseThrow();
        FunctionSymbol shout = named.member("shoutName").orElseThrow();
        assertThat(shout.returnType()).isEqualTo(StringType.INSTANCE);
        org.solvik.ast.expression.CallExprNode call = (org.solvik.ast.expression.CallExprNode) ((org.solvik.ast.expression.BinaryExprNode) ((org.solvik.ast.statement.ReturnStmtNode) shout.declaration().body().statements().get(0)).value().orElseThrow()).right();
        org.solvik.semantic.ResolvedMethod resolved = program.methodOf(call).orElseThrow();
        assertThat(resolved.isImplicitThis()).isTrue();
        assertThat(resolved.method()).isEqualTo(named.member("name").orElseThrow());
    }

    @Test
    public void interfaceExtensionInheritsRequirementsAndDefaults() {
        CheckedProgram program = check("""
                interface Readable {
                    func read(): String

                    func readTwice(): String {
                        return read() .. read()
                    }
                }
                interface Writable {
                    func write(value: String): Unit
                }
                interface Stream extends Readable, Writable {
                }
                class Buffer implements Stream {
                    func read(): String {
                        return "x"
                    }

                    func write(value: String): Unit {
                    }
                }
                """);
        InterfaceSymbol stream = program.interfaceSymbol("Stream").orElseThrow();
        assertThat(stream.superInterfaces().stream().map(InterfaceSymbol::name).collect(Collectors.toList())).isEqualTo(List.of("Readable", "Writable"));
        assertThat(stream.members().stream().map(FunctionSymbol::name).collect(Collectors.toList())).isEqualTo(List.of("read", "readTwice", "write"));

        ClassSymbol buffer = program.classSymbol("Buffer").orElseThrow();
        // `readTwice` arrives only through the extended interface's default.
        assertThat(buffer.interfaceImplementation("readTwice").orElseThrow()).isEqualTo(program.interfaceSymbol("Readable").orElseThrow().member("readTwice").orElseThrow());
        assertThat(buffer.interfaceImplementation("write").orElseThrow()).isEqualTo(buffer.declaredMethods().get(1));
    }

    @Test
    public void classInheritsConformanceFromItsSuperclass() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String
                }
                open class Base implements Named {
                    func name(): String {
                        return "base"
                    }
                }
                class Derived extends Base {
                }
                """);
        ClassSymbol derived = program.classSymbol("Derived").orElseThrow();
        assertThat(derived.allInterfaces().stream().map(InterfaceSymbol::name).collect(Collectors.toList())).isEqualTo(List.of("Named"));
        assertThat(derived.interfaceImplementation("name").orElseThrow()).isEqualTo(program.classSymbol("Base").orElseThrow().declaredMethods().get(0));
    }

    @Test
    public void aClassMethodTakesPrecedenceOverAnInterfaceDefault() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String

                    func greeting(): String {
                        return "Hello " .. name()
                    }
                }
                class User implements Named {
                    func name(): String {
                        return "Doug"
                    }

                    func greeting(): String {
                        return "Hi " .. name()
                    }
                }
                """);
        ClassSymbol user = program.classSymbol("User").orElseThrow();
        // The class's own method wins over the default, and needs no `override` modifier.
        assertThat(user.interfaceImplementation("greeting").orElseThrow().name()).isEqualTo("greeting");
        assertThat(user.interfaceImplementation("greeting").orElseThrow().isInterfaceMember()).isFalse();
        assertThat(user.declaredMethods().size()).isEqualTo(2);
    }

    @Test
    public void aSuperclassMethodTakesPrecedenceOverAnInterfaceDefault() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String

                    func greeting(): String {
                        return "Hello " .. name()
                    }
                }
                open class Base {
                    func greeting(): String {
                        return "Hello base"
                    }
                }
                class Derived extends Base implements Named {
                    func name(): String {
                        return "derived"
                    }
                }
                """);
        ClassSymbol derived = program.classSymbol("Derived").orElseThrow();
        assertThat(derived.interfaceImplementation("greeting").orElseThrow()).isEqualTo(program.classSymbol("Base").orElseThrow().declaredMethods().get(0));
    }

    @Test
    public void aCovariantImplementationOfARequirementIsAccepted() {
        CheckedProgram program = check("""
                open class Name {
                    func text(): String {
                        return "n"
                    }
                }
                interface Named {
                    func name(): Name
                }
                class User implements Named {
                    func name(): Name {
                        return Name()
                    }
                }
                """);
        ClassSymbol user = program.classSymbol("User").orElseThrow();
        assertThat(user.interfaceSignatureConflicts().isEmpty()).isTrue();
        assertThat(user.interfaceImplementation("name").orElseThrow().name()).isEqualTo("name");
    }

    @Test
    public void interfaceTypeReceivesImplementingClassValues() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String
                }
                class User implements Named {
                    func name(): String {
                        return "Doug"
                    }
                }
                func use(named: Named): String {
                    return named.name()
                }
                    val user = User()
                    val named: Named = user
                    print(use(named))
                """);
        Type named = program.interfaceSymbol("Named").orElseThrow().type();
        Type userType = program.classSymbol("User").orElseThrow().type();
        assertThat(userType.isAssignableTo(named)).isTrue();
        assertThat(named.isAssignableTo(ObjectType.INSTANCE)).isTrue();
        assertThat(named.isAssignableTo(userType)).isFalse();
    }

    @Test
    public void callThroughAnInterfaceTypedReceiverIsRecorded() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String
                }
                class User implements Named {
                    func name(): String {
                        return "Doug"
                    }
                }
                func use(named: Named): String {
                    return named.name()
                }
                """);
        InterfaceSymbol named = program.interfaceSymbol("Named").orElseThrow();
        org.solvik.ast.declaration.FunctionDeclNode use = program.function("use").orElseThrow().declaration();
        org.solvik.ast.expression.CallExprNode call = (org.solvik.ast.expression.CallExprNode) ((org.solvik.ast.statement.ReturnStmtNode) use.body().statements().get(0)).value().orElseThrow();
        org.solvik.semantic.ResolvedMethod resolved = program.methodOf(call).orElseThrow();
        assertThat(resolved.isImplicitThis()).isFalse();
        assertThat(resolved.method()).isEqualTo(named.member("name").orElseThrow());
        assertThat(program.typeOf(call).orElseThrow()).isEqualTo(StringType.INSTANCE);
    }

    @Test
    public void interfaceRequirementAcceptsABooleanReturnTypeAndArgumentTyping() {
        CheckedProgram program = check("""
                interface Filter {
                    func accepts(value: Int): Boolean
                }
                class Even implements Filter {
                    func accepts(value: Int): Boolean {
                        return value == 0
                    }
                }
                """);
        InterfaceSymbol filter = program.interfaceSymbol("Filter").orElseThrow();
        FunctionSymbol accepts = filter.member("accepts").orElseThrow();
        assertThat(accepts.parameters().size()).isEqualTo(1);
        assertThat(accepts.parameters().get(0).type()).isEqualTo(IntType.INSTANCE);
        assertThat(accepts.returnType()).isEqualTo(BooleanType.INSTANCE);
        assertThat(program.classSymbol("Even").orElseThrow().interfaceSignatureConflicts().isEmpty()).isTrue();
    }

    @Test
    public void aRedeclaredInterfaceMemberHidesTheInheritedOne() {
        CheckedProgram program = check("""
                interface Base {
                    func label(): String
                }
                interface Derived extends Base {
                    func label(): String
                }
                class C implements Derived {
                    func label(): String {
                        return "c"
                    }
                }
                """);
        InterfaceSymbol derived = program.interfaceSymbol("Derived").orElseThrow();
        // The redeclaration replaces the extended signature, so it is not a conflict for C.
        assertThat(derived.membersNamed("label").size()).isEqualTo(1);
        assertThat(program.classSymbol("C").orElseThrow().conflictingInterfaceRequirements().isEmpty()).isTrue();
    }

    @Test
    public void diamondExtensionSharesOneDefaultWithoutConflict() {
        CheckedProgram program = check("""
                interface Root {
                    func greet(): String {
                        return "hi"
                    }
                }
                interface Left extends Root {
                }
                interface Right extends Root {
                }
                class C implements Left, Right {
                }
                """);
        ClassSymbol c = program.classSymbol("C").orElseThrow();
        // The same default symbol is reached along both paths, which is not two conflicting defaults.
        assertThat(c.conflictingInterfaceRequirements().isEmpty()).isTrue();
        assertThat(c.interfaceImplementation("greet").orElseThrow()).isEqualTo(program.interfaceSymbol("Root").orElseThrow().member("greet").orElseThrow());
    }

    @Test
    public void aDefaultMethodBodyCanReadItsOwnMembersThroughThis() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String

                    func greeting(): String {
                        return "Hello " .. this.name()
                    }
                }
                """);
        InterfaceSymbol named = program.interfaceSymbol("Named").orElseThrow();
        FunctionSymbol greeting = named.member("greeting").orElseThrow();
        org.solvik.ast.expression.CallExprNode call = (org.solvik.ast.expression.CallExprNode) ((org.solvik.ast.expression.BinaryExprNode) ((org.solvik.ast.statement.ReturnStmtNode) greeting.declaration().body().statements().get(0)).value().orElseThrow()).right();
        org.solvik.semantic.ResolvedMethod resolved = program.methodOf(call).orElseThrow();
        // `this.name()` is a call on the conforming instance, so it still dispatches virtually.
        assertThat(resolved.isImplicitThis()).isFalse();
        assertThat(resolved.isSuperCall()).isFalse();
        assertThat(resolved.method()).isEqualTo(named.member("name").orElseThrow());
        assertThat(program.typeOf(call).orElseThrow()).isEqualTo(StringType.INSTANCE);
    }

    @Test
    public void aClassPropertyMayHoldAnInterfaceType() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String
                }
                class User implements Named {
                    func name(): String {
                        return "Doug"
                    }
                }
                class Holder {
                    val face: Named

                    Holder(face: Named) {
                        this.face = face
                    }

                    func use(): String {
                        return this.face.name()
                    }
                }
                """);
        InterfaceSymbol namedFace = program.interfaceSymbol("Named").orElseThrow();
        Type named = namedFace.type();
        ClassSymbol holder = program.classSymbol("Holder").orElseThrow();
        // The written property type is the interface itself, never a property of the interface.
        assertThat(holder.declaration().properties().get(0).declaredType().orElseThrow().name()).isEqualTo("Named");
        org.solvik.ast.expression.CallExprNode call = (org.solvik.ast.expression.CallExprNode) ((org.solvik.ast.statement.ReturnStmtNode) holder.declaration().methods().get(0).body().statements().get(0)).value().orElseThrow();
        org.solvik.ast.expression.MemberAccessExprNode member = (org.solvik.ast.expression.MemberAccessExprNode) call.callee();
        org.solvik.ast.expression.MemberAccessExprNode read = (org.solvik.ast.expression.MemberAccessExprNode) member.receiver();
        // `this.face` is a property of Holder, never a member of the interface.
        assertThat(program.propertyOf(read).orElseThrow().name()).isEqualTo("face");
        assertThat(program.typeOf(read).orElseThrow()).isEqualTo(named);
        // `name()` resolves against the interface and its result is the requirement's return type.
        assertThat(program.methodOf(call).orElseThrow().method()).isEqualTo(namedFace.member("name").orElseThrow());
        assertThat(program.typeOf(call).orElseThrow()).isEqualTo(StringType.INSTANCE);
    }

    @Test
    public void anInterfaceTypeIsANominalInterfaceType() {
        CheckedProgram program = check("""
                interface A {
                    func a(): Int
                }
                interface B {
                    func b(): Int
                }
                """);
        InterfaceType a = (InterfaceType) program.interfaceSymbol("A").orElseThrow().type();
        InterfaceType b = (InterfaceType) program.interfaceSymbol("B").orElseThrow().type();
        assertThat(a.isAssignableTo(b)).isFalse();
        assertThat(b.isAssignableTo(a)).isFalse();
        assertThat(a.isAssignableTo(a)).isTrue();
    }
}
