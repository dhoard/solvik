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
import static org.junit.Assert.assertSame;
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
import org.solvik.semantic.PropertySymbol;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;
import org.solvik.type.StringType;
import org.solvik.type.Type;

/**
 * Positive Phase 9 semantic tests: a delegate satisfies an interface requirement, explicit and
 * inherited methods outrank it, it outranks an interface default, inherited forwarding is reused, and
 * delegation remains nominal. The synthesized forwarding method's shape is checked directly.
 */
public final class SolvikDelegateSemanticTest {

    private static CheckedProgram check(String text) {
        CompilationUnitNode unit = parseOk("delegate.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertTrue("analysis must succeed: " + result.diagnostics().all(), result.isSuccess());
        return result.program().orElseThrow();
    }

    /** A concrete implementor of {@code Repository}, reused by the positive programs below. */
    private static final String MEMORY_REPOSITORY = """
            class MemoryRepository implements Repository {
                func save(value: String): Unit {
                }
            }
            """;

    @Test
    public void delegateSatisfiesARequirement() {
        CheckedProgram program = check("""
                interface Repository {
                    func save(value: String): Unit
                }
                """ + MEMORY_REPOSITORY + """
                class UserService implements Repository {
                    delegate val repository: Repository = MemoryRepository()
                }
                """);
        ClassSymbol service = program.classSymbol("UserService").orElseThrow();
        FunctionSymbol save = service.interfaceImplementation("save").orElseThrow();
        assertTrue(save.isSynthesized());
        assertEquals("save", save.name());
        assertEquals(1, save.parameters().size());
        assertEquals(StringType.INSTANCE, save.parameters().get(0).type());
        // The forwarding method remembers the delegate property it reads and the member it invokes.
        assertEquals(service.property("repository").orElseThrow(), save.delegateProperty());
        assertEquals("save", save.forwardedDelegate().name());
        assertEquals(service.property("repository").orElseThrow(), service.delegatedRequirement("save").orElseThrow());
        assertTrue(service.missingInterfaceRequirements().isEmpty());
        assertTrue(service.ambiguousDelegatedRequirements().isEmpty());
        assertTrue(service.delegateSignatureConflicts().isEmpty());
    }

    @Test
    public void delegateIsAnImmutableTypedProperty() {
        CheckedProgram program = check("""
                interface Repository {
                    func save(value: String): Unit
                }
                """ + MEMORY_REPOSITORY + """
                class UserService implements Repository {
                    delegate val repository: Repository = MemoryRepository()
                }
                """);
        PropertySymbol repository = program.classSymbol("UserService").orElseThrow().property("repository").orElseThrow();
        assertTrue(repository.isDelegate());
        assertFalse(repository.isMutable());
        assertEquals(1, program.classSymbol("UserService").orElseThrow().declaredProperties().size());
    }

    @Test
    public void explicitMethodTakesPrecedenceOverADelegate() {
        CheckedProgram program = check("""
                interface Repository {
                    func save(value: String): Unit
                }
                """ + MEMORY_REPOSITORY + """
                class UserService implements Repository {
                    delegate val repository: Repository = MemoryRepository()

                    func save(value: String): Unit {
                    }
                }
                """);
        ClassSymbol service = program.classSymbol("UserService").orElseThrow();
        FunctionSymbol save = service.interfaceImplementation("save").orElseThrow();
        assertFalse(save.isSynthesized());
        assertEquals("save", save.name());
        assertEquals(1, service.declaredMethods().size());
        assertTrue(service.delegatedRequirement("save").isEmpty());
    }

    @Test
    public void inheritedMethodTakesPrecedenceOverADelegate() {
        CheckedProgram program = check("""
                interface Repository {
                    func save(value: String): Unit
                }
                """ + MEMORY_REPOSITORY + """
                open class Base {
                    func save(value: String): Unit {
                    }
                }
                class UserService extends Base implements Repository {
                    delegate val repository: Repository = MemoryRepository()
                }
                """);
        ClassSymbol service = program.classSymbol("UserService").orElseThrow();
        FunctionSymbol save = service.interfaceImplementation("save").orElseThrow();
        assertEquals(program.classSymbol("Base").orElseThrow().declaredMethods().get(0), save);
        assertFalse(save.isSynthesized());
        assertTrue(service.delegatedRequirement("save").isEmpty());
    }

    @Test
    public void delegateTakesPrecedenceOverAnInterfaceDefault() {
        CheckedProgram program = check("""
                interface Repository {
                    func save(value: String): Unit

                    func saveTwice(value: String): Unit {
                        save(value)
                        save(value)
                    }
                }
                class MemoryRepository implements Repository {
                    func save(value: String): Unit {
                    }
                }
                class UserService implements Repository {
                    delegate val repository: Repository = MemoryRepository()
                }
                """);
        ClassSymbol service = program.classSymbol("UserService").orElseThrow();
        // The delegate's contract exposes both the requirement and the default, and the delegated
        // implementation outranks the default, so both are forwarding methods; the default body only
        // runs on the delegate object itself.
        assertTrue(service.interfaceImplementation("save").orElseThrow().isSynthesized());
        assertTrue(service.interfaceImplementation("saveTwice").orElseThrow().isSynthesized());
        assertEquals(service.property("repository").orElseThrow(), service.delegatedRequirement("save").orElseThrow());
        assertEquals(service.property("repository").orElseThrow(), service.delegatedRequirement("saveTwice").orElseThrow());
    }

    @Test
    public void delegateOverridesAnInterfaceDefaultItAlsoSupplies() {
        CheckedProgram program = check("""
                interface Greeter {
                    func greet(): String {
                        return "default"
                    }
                }
                class DefaultGreeter implements Greeter {
                }
                class Service implements Greeter {
                    delegate val greeter: Greeter = DefaultGreeter()
                }
                """);
        ClassSymbol service = program.classSymbol("Service").orElseThrow();
        // Both a default and a delegate can supply `greet`; the delegate wins by the architecture
        // precedence and is installed in the virtual table.
        assertTrue(service.interfaceImplementation("greet").orElseThrow().isSynthesized());
        assertSame(service.interfaceImplementation("greet").orElseThrow(), service.method("greet").orElseThrow());
        assertEquals(service.property("greeter").orElseThrow(), service.delegatedRequirement("greet").orElseThrow());
    }

    @Test
    public void aSubclassInheritsTheForwardingMethodFromItsSuperclass() {
        CheckedProgram program = check("""
                interface Repository {
                    func save(value: String): Unit
                }
                """ + MEMORY_REPOSITORY + """
                open class UserService implements Repository {
                    delegate val repository: Repository = MemoryRepository()
                }
                class AuditedService extends UserService {
                }
                """);
        ClassSymbol base = program.classSymbol("UserService").orElseThrow();
        ClassSymbol derived = program.classSymbol("AuditedService").orElseThrow();
        FunctionSymbol forwarded = base.interfaceImplementation("save").orElseThrow();
        assertTrue(forwarded.isSynthesized());
        // The subclass inherits the superclass's forwarding implementation rather than creating one.
        assertSame(forwarded, derived.interfaceImplementation("save").orElseThrow());
        assertSame(forwarded, derived.method("save").orElseThrow());
    }

    @Test
    public void delegatePropertyCanBeWrittenInConstructor() {
        CheckedProgram program = check("""
                interface Repository {
                    func save(value: String): Unit
                }
                class UserService implements Repository {
                    delegate val repository: Repository

                    UserService(repository: Repository) {
                        this.repository = repository
                    }
                }
                """);
        ClassSymbol service = program.classSymbol("UserService").orElseThrow();
        assertTrue(service.interfaceImplementation("save").orElseThrow().isSynthesized());
        Type repositoryType = service.property("repository").orElseThrow().type();
        assertEquals(program.interfaceSymbol("Repository").orElseThrow().type(), repositoryType);
    }

    @Test
    public void twoDelegatesWithDistinctContractsResolveDistinctMembers() {
        CheckedProgram program = check("""
                interface Reader {
                    func read(): String
                }
                interface Writer {
                    func write(value: String): Unit
                }
                class Both implements Reader, Writer {
                    delegate val reader: Reader
                    delegate val writer: Writer

                    Both(reader: Reader, writer: Writer) {
                        this.reader = reader
                        this.writer = writer
                    }
                }
                """);
        ClassSymbol both = program.classSymbol("Both").orElseThrow();
        assertTrue(both.ambiguousDelegatedRequirements().isEmpty());
        assertTrue(both.interfaceImplementation("read").orElseThrow().isSynthesized());
        assertTrue(both.interfaceImplementation("write").orElseThrow().isSynthesized());
        assertEquals(both.property("reader").orElseThrow(), both.delegatedRequirement("read").orElseThrow());
        assertEquals(both.property("writer").orElseThrow(), both.delegatedRequirement("write").orElseThrow());
    }

    @Test
    public void aDiamondDelegateContractIsNotAmbiguous() {
        CheckedProgram program = check("""
                interface Root {
                    func greet(): String
                }
                interface Left extends Root {
                }
                interface Right extends Root {
                }
                class Service implements Root {
                    delegate val root: Right

                    Service(root: Right) {
                        this.root = root
                    }
                }
                """);
        ClassSymbol service = program.classSymbol("Service").orElseThrow();
        // One delegate property exposes the member once even though two extension paths reach it.
        assertTrue(service.ambiguousDelegatedRequirements().isEmpty());
        assertTrue(service.interfaceImplementation("greet").orElseThrow().isSynthesized());
    }

    @Test
    public void aDelegateMaySupplyAnExtendedRequirement() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String
                }
                interface Aged extends Named {
                    func age(): Int
                }
                class Person implements Aged {
                    delegate val aged: Aged

                    Person(aged: Aged) {
                        this.aged = aged
                    }
                }
                """);
        ClassSymbol person = program.classSymbol("Person").orElseThrow();
        assertTrue(person.interfaceImplementation("name").orElseThrow().isSynthesized());
        assertTrue(person.interfaceImplementation("age").orElseThrow().isSynthesized());
        assertEquals(person.property("aged").orElseThrow(), person.delegatedRequirement("name").orElseThrow());
    }

    @Test
    public void delegationRemainsNominallyTyped() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String
                }
                class Person implements Named {
                    func name(): String {
                        return "x"
                    }
                }
                class Service implements Named {
                    delegate val named: Named

                    Service(named: Named) {
                        this.named = named
                    }
                }
                func use(named: Named): String {
                    return named.name()
                }
                    val service: Named = Service(Person())
                    println(use(service))
                """);
        Type named = program.interfaceSymbol("Named").orElseThrow().type();
        Type service = program.classSymbol("Service").orElseThrow().type();
        assertTrue(service.isAssignableTo(named));
        assertFalse(named.isAssignableTo(service));
    }

    @Test
    public void delegateMemberIsVisibleThroughTheClassDispatchTable() {
        CheckedProgram program = check("""
                interface Repository {
                    func find(id: Int): String
                }
                class Service implements Repository {
                    delegate val repository: Repository

                    Service(repository: Repository) {
                        this.repository = repository
                    }
                }
                """);
        ClassSymbol service = program.classSymbol("Service").orElseThrow();
        assertEquals(service.interfaceImplementation("find").orElseThrow(), service.method("find").orElseThrow());
        assertEquals(List.of("find"), service.methods().stream().map(FunctionSymbol::name).collect(Collectors.toList()));
    }

    @Test
    public void explicitMethodResolvesATwoDelegateConflict() {
        CheckedProgram program = check("""
                interface PrinterA {
                    func print(): Unit
                }
                interface PrinterB {
                    func print(): Unit
                }
                class X implements PrinterA, PrinterB {
                    delegate val a: PrinterA
                    delegate val b: PrinterB

                    X(a: PrinterA, b: PrinterB) {
                        this.a = a
                        this.b = b
                    }

                    func print(): Unit {
                    }
                }
                """);
        ClassSymbol x = program.classSymbol("X").orElseThrow();
        assertTrue(x.ambiguousDelegatedRequirements().isEmpty());
        assertFalse(x.interfaceImplementation("print").orElseThrow().isSynthesized());
        assertEquals(x.declaredMethods().get(0), x.interfaceImplementation("print").orElseThrow());
    }

    @Test
    public void delegateInterfaceMembersAreNotImplementedByAnUnrelatedClass() {
        CheckedProgram program = check("""
                interface Named {
                    func name(): String
                }
                class Service implements Named {
                    delegate val named: Named

                    Service(named: Named) {
                        this.named = named
                    }
                }
                """);
        InterfaceSymbol named = program.interfaceSymbol("Named").orElseThrow();
        assertEquals(List.of("name"), named.members().stream().map(FunctionSymbol::name).collect(Collectors.toList()));
    }
}
