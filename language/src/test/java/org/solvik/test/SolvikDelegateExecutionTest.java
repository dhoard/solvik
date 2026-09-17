/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.Test;

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
                    fun find(id: Int): String
                }
                class MemoryRepository implements Repository {
                    fun find(id: Int): String {
                        return "found"
                    }
                }
                class UserService implements Repository {
                    delegate val repository: Repository

                    init(repository: Repository) {
                        this.repository = repository
                    }
                }
                fun viaInterface(repository: Repository): String {
                    return repository.find(7)
                }
                fun main(): Unit {
                    val service = UserService(MemoryRepository())
                    println(viaInterface(service))
                }
                """);
        assertEquals("found", output.strip());
    }

    @Test
    public void delegatedCallThroughAClassTypedReceiverRuns() {
        String output = run("""
                interface Greeter {
                    fun greet(): String
                }
                class Service implements Greeter {
                    delegate val greeter: Greeter

                    init(greeter: Greeter) {
                        this.greeter = greeter
                    }
                }
                fun main(): Unit {
                    println(Service(Hola()).greet())
                }
                class Hola implements Greeter {
                    fun greet(): String {
                        return "hola"
                    }
                }
                """);
        assertEquals("hola", output.strip());
    }

    @Test
    public void explicitMethodOverridesADelegate() {
        String output = run("""
                interface Greeter {
                    fun greet(): String
                }
                class Hola implements Greeter {
                    fun greet(): String {
                        return "hola"
                    }
                }
                class Service implements Greeter {
                    delegate val greeter: Greeter

                    init(greeter: Greeter) {
                        this.greeter = greeter
                    }

                    fun greet(): String {
                        return "explicit"
                    }
                }
                fun viaInterface(greeter: Greeter): String {
                    return greeter.greet()
                }
                fun main(): Unit {
                    println(viaInterface(Service(Hola())))
                }
                """);
        assertEquals("explicit", output.strip());
    }

    @Test
    public void inheritedMethodOverridesADelegate() {
        String output = run("""
                interface Greeter {
                    fun greet(): String
                }
                class Hola implements Greeter {
                    fun greet(): String {
                        return "hola"
                    }
                }
                open class Base {
                    fun greet(): String {
                        return "inherited"
                    }
                }
                class Service extends Base implements Greeter {
                    delegate val greeter: Greeter

                    init(greeter: Greeter) {
                        this.greeter = greeter
                    }
                }
                fun viaInterface(greeter: Greeter): String {
                    return greeter.greet()
                }
                fun main(): Unit {
                    println(viaInterface(Service(Hola())))
                }
                """);
        assertEquals("inherited", output.strip());
    }

    @Test
    public void delegateOverridesAnInterfaceDefault() {
        String output = run("""
                interface Greeter {
                    fun greet(): String {
                        return "default"
                    }
                }
                class Hola implements Greeter {
                    fun greet(): String {
                        return "hola"
                    }
                }
                class Service implements Greeter {
                    delegate val greeter: Greeter

                    init(greeter: Greeter) {
                        this.greeter = greeter
                    }
                }
                fun main(): Unit {
                    println(Service(Hola()).greet())
                }
                """);
        assertEquals("hola", output.strip());
    }

    @Test
    public void anInterfaceDefaultCanCallADelegatedRequirement() {
        String output = run("""
                interface Repository {
                    fun find(id: Int): String

                    fun describe(id: Int): String {
                        return "repo " + find(id)
                    }
                }
                class MemoryRepository implements Repository {
                    fun find(id: Int): String {
                        return "found"
                    }
                }
                class UserService implements Repository {
                    delegate val repository: Repository

                    init(repository: Repository) {
                        this.repository = repository
                    }
                }
                fun main(): Unit {
                    val service = UserService(MemoryRepository())
                    val repository: Repository = service
                    println(repository.describe(1))
                }
                """);
        assertEquals("repo found", output.strip());
    }

    @Test
    public void forwardingDispatchesToTheActualDelegateAtRuntime() {
        String output = run("""
                interface Greeter {
                    fun greet(): String
                }
                class Hola implements Greeter {
                    fun greet(): String {
                        return "hola"
                    }
                }
                class Ciao implements Greeter {
                    fun greet(): String {
                        return "ciao"
                    }
                }
                class Service implements Greeter {
                    delegate val greeter: Greeter

                    init(greeter: Greeter) {
                        this.greeter = greeter
                    }
                }
                fun viaInterface(greeter: Greeter): String {
                    return greeter.greet()
                }
                fun main(): Unit {
                    println(viaInterface(Service(Hola())))
                    println(viaInterface(Service(Ciao())))
                }
                """);
        assertEquals("hola", output.split("\n")[0].strip());
        assertEquals("ciao", output.split("\n")[1].strip());
    }

    @Test
    public void twoDelegatesDispatchIndependentMembers() {
        String output = run("""
                interface Reader {
                    fun read(): String
                }
                interface Writer {
                    fun write(value: String): String
                }
                class FileReader implements Reader {
                    fun read(): String {
                        return "data"
                    }
                }
                class FileWriter implements Writer {
                    fun write(value: String): String {
                        return "wrote " + value
                    }
                }
                class Both implements Reader, Writer {
                    delegate val reader: Reader
                    delegate val writer: Writer

                    init(reader: Reader, writer: Writer) {
                        this.reader = reader
                        this.writer = writer
                    }
                }
                fun main(): Unit {
                    val both = Both(FileReader(), FileWriter())
                    println(both.read())
                    println(both.write("x"))
                }
                """);
        assertEquals("data", output.split("\n")[0].strip());
        assertEquals("wrote x", output.split("\n")[1].strip());
    }

    @Test
    public void aUnitReturningDelegatedMemberRunsForItsEffect() {
        String output = run("""
                interface Sink {
                    fun put(value: String): Unit
                }
                class ConsoleSink implements Sink {
                    fun put(value: String): Unit {
                        println("sink " + value)
                    }
                }
                class Service implements Sink {
                    delegate val sink: Sink

                    init(sink: Sink) {
                        this.sink = sink
                    }
                }
                fun main(): Unit {
                    val service: Sink = Service(ConsoleSink())
                    service.put("x")
                }
                """);
        assertEquals("sink x", output.strip());
    }

    @Test
    public void aSubclassInheritsAndReusesTheForwardingImplementation() {
        String output = run("""
                interface Greeter {
                    fun greet(): String
                }
                class Hola implements Greeter {
                    fun greet(): String {
                        return "hola"
                    }
                }
                open class Service implements Greeter {
                    delegate val greeter: Greeter

                    init(greeter: Greeter) {
                        this.greeter = greeter
                    }
                }
                class Audited extends Service {
                    init() {
                        super(Hola())
                    }
                }
                fun main(): Unit {
                    val greeter: Greeter = Audited()
                    println(greeter.greet())
                }
                """);
        assertEquals("hola", output.strip());
    }

    @Test
    public void delegateWithADeclarationInitializerRuns() {
        String output = run("""
                interface Greeter {
                    fun greet(): String
                }
                class Hola implements Greeter {
                    fun greet(): String {
                        return "hola"
                    }
                }
                class Service implements Greeter {
                    delegate val greeter: Greeter = Hola()
                }
                fun main(): Unit {
                    println(Service().greet())
                }
                """);
        assertEquals("hola", output.strip());
    }

    @Test
    public void anInterfaceTypedDelegatePropertyCanBeReadAndForwarded() {
        String output = run("""
                interface Greeter {
                    fun greet(): String
                }
                class Hola implements Greeter {
                    fun greet(): String {
                        return "hola"
                    }
                }
                class Service implements Greeter {
                    delegate val greeter: Greeter

                    init(greeter: Greeter) {
                        this.greeter = greeter
                    }

                    fun other(): String {
                        return this.greeter.greet()
                    }
                }
                fun main(): Unit {
                    println(Service(Hola()).other())
                }
                """);
        assertEquals("hola", output.strip());
    }

    @Test
    public void ambiguousDelegationIsACompileErrorWithNoOutput() {
        String program = """
                interface PrinterA {
                    fun print(): Unit
                }
                interface PrinterB {
                    fun print(): Unit
                }
                class X implements PrinterA, PrinterB {
                    delegate val a: PrinterA
                    delegate val b: PrinterB

                    init(a: PrinterA, b: PrinterB) {
                        this.a = a
                        this.b = b
                    }
                }
                fun main(): Unit {
                    println("unreachable")
                }
                """;
        PolyglotException failure = assertThrows(PolyglotException.class, () -> run(program));
        assertTrue(failure.getMessage(), failure.getMessage().contains("SOLV-SEM-026"));
    }

    @Test
    public void delegateSignatureConflictIsACompileErrorWithNoOutput() {
        String program = """
                interface Sink {
                    fun put(value: Int): Unit
                }
                interface StringSink {
                    fun put(value: String): Unit
                }
                class Service implements Sink {
                    delegate val sink: StringSink

                    init(sink: StringSink) {
                        this.sink = sink
                    }
                }
                fun main(): Unit {
                    println("unreachable")
                }
                """;
        PolyglotException failure = assertThrows(PolyglotException.class, () -> run(program));
        assertTrue(failure.getMessage(), failure.getMessage().contains("SOLV-SEM-027"));
    }
}
