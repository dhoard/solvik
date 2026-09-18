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
        assertThat(result.isSuccess()).as("analysis must succeed: " + result.diagnostics().all()).isTrue();
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
        assertThat(save.isSynthesized()).isTrue();
        assertThat(save.name()).isEqualTo("save");
        assertThat(save.parameters().size()).isEqualTo(1);
        assertThat(save.parameters().get(0).type()).isEqualTo(StringType.INSTANCE);
        // The forwarding method remembers the delegate property it reads and the member it invokes.
        assertThat(save.delegateProperty()).isEqualTo(service.property("repository").orElseThrow());
        assertThat(save.forwardedDelegate().name()).isEqualTo("save");
        assertThat(service.delegatedRequirement("save").orElseThrow()).isEqualTo(service.property("repository").orElseThrow());
        assertThat(service.missingInterfaceRequirements().isEmpty()).isTrue();
        assertThat(service.ambiguousDelegatedRequirements().isEmpty()).isTrue();
        assertThat(service.delegateSignatureConflicts().isEmpty()).isTrue();
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
        assertThat(repository.isDelegate()).isTrue();
        assertThat(repository.isMutable()).isFalse();
        assertThat(program.classSymbol("UserService").orElseThrow().declaredProperties().size()).isEqualTo(1);
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
        assertThat(save.isSynthesized()).isFalse();
        assertThat(save.name()).isEqualTo("save");
        assertThat(service.declaredMethods().size()).isEqualTo(1);
        assertThat(service.delegatedRequirement("save").isEmpty()).isTrue();
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
        assertThat(save).isEqualTo(program.classSymbol("Base").orElseThrow().declaredMethods().get(0));
        assertThat(save.isSynthesized()).isFalse();
        assertThat(service.delegatedRequirement("save").isEmpty()).isTrue();
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
        assertThat(service.interfaceImplementation("save").orElseThrow().isSynthesized()).isTrue();
        assertThat(service.interfaceImplementation("saveTwice").orElseThrow().isSynthesized()).isTrue();
        assertThat(service.delegatedRequirement("save").orElseThrow()).isEqualTo(service.property("repository").orElseThrow());
        assertThat(service.delegatedRequirement("saveTwice").orElseThrow()).isEqualTo(service.property("repository").orElseThrow());
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
        assertThat(service.interfaceImplementation("greet").orElseThrow().isSynthesized()).isTrue();
        assertThat(service.method("greet").orElseThrow()).isSameAs(service.interfaceImplementation("greet").orElseThrow());
        assertThat(service.delegatedRequirement("greet").orElseThrow()).isEqualTo(service.property("greeter").orElseThrow());
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
        assertThat(forwarded.isSynthesized()).isTrue();
        // The subclass inherits the superclass's forwarding implementation rather than creating one.
        assertThat(derived.interfaceImplementation("save").orElseThrow()).isSameAs(forwarded);
        assertThat(derived.method("save").orElseThrow()).isSameAs(forwarded);
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
        assertThat(service.interfaceImplementation("save").orElseThrow().isSynthesized()).isTrue();
        Type repositoryType = service.property("repository").orElseThrow().type();
        assertThat(repositoryType).isEqualTo(program.interfaceSymbol("Repository").orElseThrow().type());
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
        assertThat(both.ambiguousDelegatedRequirements().isEmpty()).isTrue();
        assertThat(both.interfaceImplementation("read").orElseThrow().isSynthesized()).isTrue();
        assertThat(both.interfaceImplementation("write").orElseThrow().isSynthesized()).isTrue();
        assertThat(both.delegatedRequirement("read").orElseThrow()).isEqualTo(both.property("reader").orElseThrow());
        assertThat(both.delegatedRequirement("write").orElseThrow()).isEqualTo(both.property("writer").orElseThrow());
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
        assertThat(service.ambiguousDelegatedRequirements().isEmpty()).isTrue();
        assertThat(service.interfaceImplementation("greet").orElseThrow().isSynthesized()).isTrue();
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
        assertThat(person.interfaceImplementation("name").orElseThrow().isSynthesized()).isTrue();
        assertThat(person.interfaceImplementation("age").orElseThrow().isSynthesized()).isTrue();
        assertThat(person.delegatedRequirement("name").orElseThrow()).isEqualTo(person.property("aged").orElseThrow());
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
        assertThat(service.isAssignableTo(named)).isTrue();
        assertThat(named.isAssignableTo(service)).isFalse();
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
        assertThat(service.method("find").orElseThrow()).isEqualTo(service.interfaceImplementation("find").orElseThrow());
        assertThat(service.methods().stream().map(FunctionSymbol::name).collect(Collectors.toList())).isEqualTo(List.of("find"));
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
        assertThat(x.ambiguousDelegatedRequirements().isEmpty()).isTrue();
        assertThat(x.interfaceImplementation("print").orElseThrow().isSynthesized()).isFalse();
        assertThat(x.interfaceImplementation("print").orElseThrow()).isEqualTo(x.declaredMethods().get(0));
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
        assertThat(named.members().stream().map(FunctionSymbol::name).collect(Collectors.toList())).isEqualTo(List.of("name"));
    }
}
