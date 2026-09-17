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

import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
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
        assertTrue("analysis must succeed: " + result.diagnostics().all(), result.isSuccess());
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
        assertEquals("Named", named.name());
        assertTrue(named.type() instanceof InterfaceType);
        assertEquals(List.of("name", "greeting"), named.declaredMembers().stream().map(FunctionSymbol::name).collect(Collectors.toList()));

        FunctionSymbol name = named.member("name").orElseThrow();
        assertTrue(name.isAbstractSignature());
        assertFalse(name.hasImplementation());
        assertEquals(StringType.INSTANCE, name.returnType());

        FunctionSymbol greeting = named.member("greeting").orElseThrow();
        assertTrue(greeting.hasImplementation());
        assertTrue(greeting.isInterfaceMember());
        assertEquals("Named", greeting.interfaceOwner().name());
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
        assertEquals(List.of("Named"), user.interfaces().stream().map(InterfaceSymbol::name).collect(Collectors.toList()));
        assertEquals(user.declaredMethods().get(0), user.interfaceImplementation("name").orElseThrow());
        assertTrue(user.method("name").isPresent());
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
        assertEquals(List.of("Named", "Aged"), user.interfaces().stream().map(InterfaceSymbol::name).collect(Collectors.toList()));
        assertEquals("name", user.interfaceImplementation("name").orElseThrow().name());
        assertEquals("age", user.interfaceImplementation("age").orElseThrow().name());
        assertEquals(List.of("name", "age"), user.methods().stream().map(FunctionSymbol::name).collect(Collectors.toList()));
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
        assertEquals(named.member("greeting").orElseThrow(), user.interfaceImplementation("greeting").orElseThrow());
        assertEquals(user.declaredMethods().get(0), user.interfaceImplementation("name").orElseThrow());
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
            assertTrue(greeting.isInterfaceMember());
            assertEquals("greeting", greeting.name());
            // The same default symbol is shared by every conforming class, not copied per class.
            assertEquals(program.interfaceSymbol("Named").orElseThrow().member("greeting").orElseThrow(), greeting);
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
        assertEquals(StringType.INSTANCE, shout.returnType());
        org.solvik.ast.expression.CallExprNode call = (org.solvik.ast.expression.CallExprNode) ((org.solvik.ast.expression.BinaryExprNode) ((org.solvik.ast.statement.ReturnStmtNode) shout.declaration().body().statements().get(0)).value().orElseThrow()).right();
        org.solvik.semantic.ResolvedMethod resolved = program.methodOf(call).orElseThrow();
        assertTrue(resolved.isImplicitThis());
        assertEquals(named.member("name").orElseThrow(), resolved.method());
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
        assertEquals(List.of("Readable", "Writable"), stream.superInterfaces().stream().map(InterfaceSymbol::name).collect(Collectors.toList()));
        assertEquals(List.of("read", "readTwice", "write"), stream.members().stream().map(FunctionSymbol::name).collect(Collectors.toList()));

        ClassSymbol buffer = program.classSymbol("Buffer").orElseThrow();
        // `readTwice` arrives only through the extended interface's default.
        assertEquals(program.interfaceSymbol("Readable").orElseThrow().member("readTwice").orElseThrow(), buffer.interfaceImplementation("readTwice").orElseThrow());
        assertEquals(buffer.declaredMethods().get(1), buffer.interfaceImplementation("write").orElseThrow());
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
        assertEquals(List.of("Named"), derived.allInterfaces().stream().map(InterfaceSymbol::name).collect(Collectors.toList()));
        assertEquals(program.classSymbol("Base").orElseThrow().declaredMethods().get(0), derived.interfaceImplementation("name").orElseThrow());
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
        assertEquals("greeting", user.interfaceImplementation("greeting").orElseThrow().name());
        assertFalse(user.interfaceImplementation("greeting").orElseThrow().isInterfaceMember());
        assertEquals(2, user.declaredMethods().size());
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
        assertEquals(program.classSymbol("Base").orElseThrow().declaredMethods().get(0), derived.interfaceImplementation("greeting").orElseThrow());
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
        assertTrue(user.interfaceSignatureConflicts().isEmpty());
        assertEquals("name", user.interfaceImplementation("name").orElseThrow().name());
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
        assertTrue(userType.isAssignableTo(named));
        assertTrue(named.isAssignableTo(ObjectType.INSTANCE));
        assertFalse(named.isAssignableTo(userType));
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
        assertFalse(resolved.isImplicitThis());
        assertEquals(named.member("name").orElseThrow(), resolved.method());
        assertEquals(StringType.INSTANCE, program.typeOf(call).orElseThrow());
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
        assertEquals(1, accepts.parameters().size());
        assertEquals(IntType.INSTANCE, accepts.parameters().get(0).type());
        assertEquals(BooleanType.INSTANCE, accepts.returnType());
        assertTrue(program.classSymbol("Even").orElseThrow().interfaceSignatureConflicts().isEmpty());
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
        assertEquals(1, derived.membersNamed("label").size());
        assertTrue(program.classSymbol("C").orElseThrow().conflictingInterfaceRequirements().isEmpty());
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
        assertTrue(c.conflictingInterfaceRequirements().isEmpty());
        assertEquals(program.interfaceSymbol("Root").orElseThrow().member("greet").orElseThrow(), c.interfaceImplementation("greet").orElseThrow());
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
        assertFalse(resolved.isImplicitThis());
        assertFalse(resolved.isSuperCall());
        assertEquals(named.member("name").orElseThrow(), resolved.method());
        assertEquals(StringType.INSTANCE, program.typeOf(call).orElseThrow());
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
        assertEquals("Named", holder.declaration().properties().get(0).declaredType().orElseThrow().name());
        org.solvik.ast.expression.CallExprNode call = (org.solvik.ast.expression.CallExprNode) ((org.solvik.ast.statement.ReturnStmtNode) holder.declaration().methods().get(0).body().statements().get(0)).value().orElseThrow();
        org.solvik.ast.expression.MemberAccessExprNode member = (org.solvik.ast.expression.MemberAccessExprNode) call.callee();
        org.solvik.ast.expression.MemberAccessExprNode read = (org.solvik.ast.expression.MemberAccessExprNode) member.receiver();
        // `this.face` is a property of Holder, never a member of the interface.
        assertEquals("face", program.propertyOf(read).orElseThrow().name());
        assertEquals(named, program.typeOf(read).orElseThrow());
        // `name()` resolves against the interface and its result is the requirement's return type.
        assertEquals(namedFace.member("name").orElseThrow(), program.methodOf(call).orElseThrow().method());
        assertEquals(StringType.INSTANCE, program.typeOf(call).orElseThrow());
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
        assertFalse(a.isAssignableTo(b));
        assertFalse(b.isAssignableTo(a));
        assertTrue(a.isAssignableTo(a));
    }
}
