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
import static org.solvik.test.SolvikTestSupport.expectThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

/**
 * End-to-end Phase 9 execution tests: a delegated method supplies an interface requirement, explicit
 * and inherited methods win over a delegate, a delegate wins over an interface default, an interface
 * default can call a delegated member, two delegates dispatch independently, and an ambiguous
 * delegation is a compile error that produces no output.
 */
public final class SolvikDelegateExecutionTest {

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source, "test.sol"));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Source build(String source, String name) {
        try {
            return Source.newBuilder("solvik", source, name).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void delegatedMethodSatisfiesAnInterfaceRequirement() {
        String output = run("""
                interface Repository {
                    method find(id: Integer): String
                }
                class MemoryRepository implements Repository {
                    method find(id: Integer): String {
                        return "found"
                    }
                }
                class UserService implements Repository {
                    delegate  repository: Repository

                    UserService(repository: Repository) {
                        this.repository = repository
                    }
                }
                func viaInterface(repository: Repository): String {
                    return repository.find(7)
                }
                    var service: UserService = UserService(MemoryRepository())
                    println(viaInterface(service))

                """);
        assertThat(output.strip()).isEqualTo("found");
    }

    @Test
    public void delegatedCallThroughAClassTypedReceiverRuns() {
        String output = run("""
                interface Greeter {
                    method greet(): String
                }
                class Service implements Greeter {
                    delegate  greeter: Greeter

                    Service(greeter: Greeter) {
                        this.greeter = greeter
                    }
                }
                    println(Service(Hola()).greet())
                class Hola implements Greeter {
                    method greet(): String {
                        return "hola"
                    }
                }

                """);
        assertThat(output.strip()).isEqualTo("hola");
    }

    @Test
    public void explicitMethodOverridesADelegate() {
        String output = run("""
                interface Greeter {
                    method greet(): String
                }
                class Hola implements Greeter {
                    method greet(): String {
                        return "hola"
                    }
                }
                class Service implements Greeter {
                    delegate  greeter: Greeter

                    Service(greeter: Greeter) {
                        this.greeter = greeter
                    }

                    method greet(): String {
                        return "explicit"
                    }
                }
                func viaInterface(greeter: Greeter): String {
                    return greeter.greet()
                }
                    println(viaInterface(Service(Hola())))

                """);
        assertThat(output.strip()).isEqualTo("explicit");
    }

    @Test
    public void inheritedMethodOverridesADelegate() {
        String output = run("""
                interface Greeter {
                    method greet(): String
                }
                class Hola implements Greeter {
                    method greet(): String {
                        return "hola"
                    }
                }
                class mutable Base {
                    method greet(): String {
                        return "inherited"
                    }
                }
                class Service extends Base implements Greeter {
                    delegate  greeter: Greeter

                    Service(greeter: Greeter) {
                        this.greeter = greeter
                    }
                }
                func viaInterface(greeter: Greeter): String {
                    return greeter.greet()
                }
                    println(viaInterface(Service(Hola())))


                """);
        assertThat(output.strip()).isEqualTo("inherited");
    }

    @Test
    public void delegateOverridesAnInterfaceDefault() {
        String output = run("""
                interface Greeter {
                    method greet(): String {
                        return "default"
                    }
                }
                class Hola implements Greeter {
                    method greet(): String {
                        return "hola"
                    }
                }
                class Service implements Greeter {
                    delegate  greeter: Greeter

                    Service(greeter: Greeter) {
                        this.greeter = greeter
                    }
                }
                    println(Service(Hola()).greet())

                """);
        assertThat(output.strip()).isEqualTo("hola");
    }

    @Test
    public void anInterfaceDefaultCanCallADelegatedRequirement() {
        String output = run("""
                interface Repository {
                    method find(id: Integer): String

                    method describe(id: Integer): String {
                        return "repo " .. find(id)
                    }
                }
                class MemoryRepository implements Repository {
                    method find(id: Integer): String {
                        return "found"
                    }
                }
                class UserService implements Repository {
                    delegate  repository: Repository

                    UserService(repository: Repository) {
                        this.repository = repository
                    }
                }
                    var service: UserService = UserService(MemoryRepository())
                    var repository: Repository = service
                    println(repository.describe(1))

                """);
        assertThat(output.strip()).isEqualTo("repo found");
    }

    @Test
    public void forwardingDispatchesToTheActualDelegateAtRuntime() {
        String output = run("""
                interface Greeter {
                    method greet(): String
                }
                class Hola implements Greeter {
                    method greet(): String {
                        return "hola"
                    }
                }
                class Ciao implements Greeter {
                    method greet(): String {
                        return "ciao"
                    }
                }
                class Service implements Greeter {
                    delegate  greeter: Greeter

                    Service(greeter: Greeter) {
                        this.greeter = greeter
                    }
                }
                func viaInterface(greeter: Greeter): String {
                    return greeter.greet()
                }
                    println(viaInterface(Service(Hola())))
                    println(viaInterface(Service(Ciao())))

                """);
        assertThat(output.split("\n")[0].strip()).isEqualTo("hola");
        assertThat(output.split("\n")[1].strip()).isEqualTo("ciao");
    }

    @Test
    public void twoDelegatesDispatchIndependentMembers() {
        String output = run("""
                interface Reader {
                    method read(): String
                }
                interface Writer {
                    method write(value: String): String
                }
                class FileReader implements Reader {
                    method read(): String {
                        return "data"
                    }
                }
                class FileWriter implements Writer {
                    method write(value: String): String {
                        return "wrote " .. value
                    }
                }
                class Both implements Reader, Writer {
                    delegate  reader: Reader
                    delegate  writer: Writer

                    Both(reader: Reader, writer: Writer) {
                        this.reader = reader
                        this.writer = writer
                    }
                }
                    var both: Both = Both(FileReader(), FileWriter())
                    println(both.read())
                    println(both.write("x"))

                """);
        assertThat(output.split("\n")[0].strip()).isEqualTo("data");
        assertThat(output.split("\n")[1].strip()).isEqualTo("wrote x");
    }

    @Test
    public void aUnitReturningDelegatedMemberRunsForItsEffect() {
        String output = run("""
                interface Sink {
                    method put(value: String)
                }
                class ConsoleSink implements Sink {
                    method put(value: String) {
                        println("sink " .. value)
                    }
                }
                class Service implements Sink {
                    delegate  sink: Sink

                    Service(sink: Sink) {
                        this.sink = sink
                    }
                }
                    var service: Sink = Service(ConsoleSink())
                    service.put("x")

                """);
        assertThat(output.strip()).isEqualTo("sink x");
    }

    @Test
    public void aSubclassInheritsAndReusesTheForwardingImplementation() {
        String output = run("""
                interface Greeter {
                    method greet(): String
                }
                class Hola implements Greeter {
                    method greet(): String {
                        return "hola"
                    }
                }
                class mutable Service implements Greeter {
                    delegate  greeter: Greeter

                    Service(greeter: Greeter) {
                        this.greeter = greeter
                    }
                }
                class Audited extends Service {
                    Audited() {
                        super(Hola())
                    }
                }
                    var greeter: Greeter = Audited()
                    println(greeter.greet())

                """);
        assertThat(output.strip()).isEqualTo("hola");
    }

    @Test
    public void delegateWithADeclarationInitializerRuns() {
        String output = run("""
                interface Greeter {
                    method greet(): String
                }
                class Hola implements Greeter {
                    method greet(): String {
                        return "hola"
                    }
                }
                class Service implements Greeter {
                    delegate  greeter: Greeter = Hola()
                }
                    println(Service().greet())

                """);
        assertThat(output.strip()).isEqualTo("hola");
    }

    @Test
    public void anInterfaceTypedDelegatePropertyCanBeReadAndForwarded() {
        String output = run("""
                interface Greeter {
                    method greet(): String
                }
                class Hola implements Greeter {
                    method greet(): String {
                        return "hola"
                    }
                }
                class Service implements Greeter {
                    delegate  greeter: Greeter

                    Service(greeter: Greeter) {
                        this.greeter = greeter
                    }

                    method other(): String {
                        return this.greeter.greet()
                    }
                }
                    println(Service(Hola()).other())

                """);
        assertThat(output.strip()).isEqualTo("hola");
    }

    @Test
    public void ambiguousDelegationIsACompileErrorWithNoOutput() {
        String program = """
                interface PrinterA {
                    method print()
                }
                interface PrinterB {
                    method print()
                }
                class X implements PrinterA, PrinterB {
                    delegate  a: PrinterA
                    delegate  b: PrinterB

                    X(a: PrinterA, b: PrinterB) {
                        this.a = a
                        this.b = b
                    }
                }
                    println("unreachable")

                """;
        PolyglotException failure = expectThrows(PolyglotException.class, () -> run(program));
        assertThat(failure.getMessage().contains("SOLV-SEM-026")).as(failure.getMessage()).isTrue();
    }

    @Test
    public void delegateSignatureConflictIsACompileErrorWithNoOutput() {
        String program = """
                interface Sink {
                    method put(value: Integer)
                }
                interface StringSink {
                    method put(value: String)
                }
                class Service implements Sink {
                    delegate  sink: StringSink

                    Service(sink: StringSink) {
                        this.sink = sink
                    }
                }
                    println("unreachable")

                """;
        PolyglotException failure = expectThrows(PolyglotException.class, () -> run(program));
        assertThat(failure.getMessage().contains("SOLV-SEM-027")).as(failure.getMessage()).isTrue();
    }

    /**
     * An immediate {@code super.method(...)} call reaches an interface requirement a superclass supplies
     * only through a {@code delegate}.
     *
     * <p>A requirement met solely by a delegate has no declared method in the superclass — the dispatch
     * table holds a synthesized forwarding method there, and synthesized methods carry no declaration
     * node. Resolving the {@code super} target by declaration alone missed that entry and failed during
     * lowering with "no lowered method for '<name>'", an internal error on a program nothing had faulted.
     */
    @Test
    public void superCallReachesADelegatedRequirementInASuperclass() {
        assertThat(run("""
                interface Repo {
                    method find(id: Integer): String
                }

                class MemRepo implements Repo {
                    method find(id: Integer): String {
                        return "row-" .. id
                    }
                }

                class mutable Service implements Repo {
                    delegate  repo: Repo

                    Service(repo: Repo) {
                        this.repo = repo
                    }
                }

                class Sub extends Service {
                    Sub(repo: Repo) {
                        super(repo)
                    }
                    method viaSuper(): String {
                        return super.find(7)
                    }
                }

                print(Sub(MemRepo()).viaSuper())

                """)).isEqualTo("row-7");
    }
}
