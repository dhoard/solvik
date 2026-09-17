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
 * End-to-end Phase 8 execution tests: one interface, multiple interfaces, interface default methods
 * dispatched through class and interface-typed values, an explicit method overriding a default, and
 * conflicting defaults resolved explicitly all run through the Truffle AST backend.
 */
public final class SolvikInterfaceExecutionTest {

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
    public void implementingMethodRunsThroughAnInterfaceTypedParameter() {
        String output = run("""
                interface Named {
                    fun name(): String
                }
                class User implements Named {
                    val label: String

                    init(label: String) {
                        this.label = label
                    }

                    fun name(): String {
                        return this.label
                    }
                }
                fun greet(named: Named): String {
                    return named.name()
                }
                fun main(): Unit {
                    println(greet(User("Doug")))
                }
                """);
        assertEquals("Doug", output.strip());
    }

    @Test
    public void defaultMethodRunsForAClassThatImplementsOnlyTheRequirement() {
        String output = run("""
                interface Named {
                    fun name(): String

                    fun greeting(): String {
                        return "Hello " + name()
                    }
                }
                class User implements Named {
                    val label: String

                    init(label: String) {
                        this.label = label
                    }

                    fun name(): String {
                        return this.label
                    }
                }
                fun main(): Unit {
                    println(User("Doug").greeting())
                }
                """);
        assertEquals("Hello Doug", output.strip());
    }

    @Test
    public void defaultMethodDispatchesVirtuallyToTheConcreteRequirement() {
        String output = run("""
                interface Named {
                    fun name(): String

                    fun greeting(): String {
                        return "Hello " + name()
                    }
                }
                open class Base implements Named {
                    fun name(): String {
                        return "base"
                    }
                }
                class Derived implements Named {
                    fun name(): String {
                        return "derived"
                    }
                }
                fun shout(named: Named): String {
                    return named.greeting()
                }
                fun main(): Unit {
                    println(shout(Base()))
                    println(shout(Derived()))
                }
                """);
        assertEquals("Hello base", output.split("\n")[0].strip());
        assertEquals("Hello derived", output.split("\n")[1].strip());
    }

    @Test
    public void explicitMethodOverridesAnInheritedDefault() {
        String output = run("""
                interface Named {
                    fun name(): String

                    fun greeting(): String {
                        return "Hello " + name()
                    }
                }
                class User implements Named {
                    fun name(): String {
                        return "Doug"
                    }

                    fun greeting(): String {
                        return "Hi " + name()
                    }
                }
                fun main(): Unit {
                    val named: Named = User()
                    println(named.greeting())
                }
                """);
        assertEquals("Hi Doug", output.strip());
    }

    @Test
    public void multipleInterfacesDispatchIndependently() {
        String output = run("""
                interface Named {
                    fun name(): String
                }
                interface Aged {
                    fun age(): Int
                }
                class User implements Named, Aged {
                    fun name(): String {
                        return "Doug"
                    }

                    fun age(): Int {
                        return 42
                    }
                }
                fun describe(named: Named, aged: Aged): Unit {
                    print(named.name())
                    print(" ")
                    println(aged.age())
                }
                fun main(): Unit {
                    val user = User()
                    describe(user, user)
                }
                """);
        assertEquals("Doug 42", output.strip());
    }

    @Test
    public void conflictingDefaultsResolvedExplicitlyRunTheClassMethod() {
        String output = run("""
                interface A {
                    fun greet(): String {
                        return "a"
                    }
                }
                interface B {
                    fun greet(): String {
                        return "b"
                    }
                }
                class C implements A, B {
                    fun greet(): String {
                        return "c"
                    }
                }
                fun viaA(a: A): String {
                    return a.greet()
                }
                fun viaB(b: B): String {
                    return b.greet()
                }
                fun main(): Unit {
                    val c = C()
                    println(viaA(c))
                    println(viaB(c))
                }
                """);
        assertEquals("c", output.split("\n")[0].strip());
        assertEquals("c", output.split("\n")[1].strip());
    }

    @Test
    public void interfaceFromAnExtendedInterfaceIsAcceptedAsAParameterType() {
        String output = run("""
                interface Readable {
                    fun read(): String
                }
                interface Writable {
                    fun write(value: String): String
                }
                interface Stream extends Readable, Writable {
                    fun copy(): String {
                        return write(read())
                    }
                }
                class Buffer implements Stream {
                    val contents: String

                    init(contents: String) {
                        this.contents = contents
                    }

                    fun read(): String {
                        return this.contents
                    }

                    fun write(value: String): String {
                        return "wrote " + value
                    }
                }
                fun roundTrip(stream: Stream): String {
                    return stream.copy()
                }
                fun main(): Unit {
                    println(roundTrip(Buffer("x")))
                }
                """);
        assertEquals("wrote x", output.strip());
    }

    @Test
    public void classInheritsConformanceAndDispatchesThroughTheSuperclassMethod() {
        String output = run("""
                interface Named {
                    fun name(): String
                }
                open class Base implements Named {
                    fun name(): String {
                        return "base"
                    }
                }
                class Derived extends Base {
                }
                fun viaInterface(named: Named): String {
                    return named.name()
                }
                fun main(): Unit {
                    println(viaInterface(Derived()))
                }
                """);
        assertEquals("base", output.strip());
    }

    @Test
    public void interfaceRequirementIsReachedThroughAnAnyTypedBuiltinArgument() {
        String output = run("""
                interface Named {
                    fun name(): String
                }
                class User implements Named {
                    fun name(): String {
                        return "Doug"
                    }
                }
                fun main(): Unit {
                    val user = User()
                    print(user.name())
                    println("")
                }
                """);
        assertEquals("Doug", output.strip());
    }

    @Test
    public void interfaceMethodRunsInsideALoopOnAnInterfaceTypedLocal() {
        String output = run("""
                interface Counter {
                    fun tick(value: Int): Int
                }
                class Doubler implements Counter {
                    fun tick(value: Int): Int {
                        return value * 2
                    }
                }
                fun total(counter: Counter, limit: Int): Int {
                    var sum = 0
                    for (var i = 0; i < limit; i = i + 1) {
                        sum = sum + counter.tick(i)
                    }
                    return sum
                }
                fun main(): Unit {
                    val counter: Counter = Doubler()
                    println(total(counter, 4))
                }
                """);
        assertEquals("12", output.strip());
    }

    @Test
    public void aDefaultMethodMayCallAnotherDefaultMethod() {
        String output = run("""
                interface Named {
                    fun name(): String

                    fun greeting(): String {
                        return "Hello " + name()
                    }

                    fun announcement(): String {
                        return greeting() + "!"
                    }
                }
                class User implements Named {
                    fun name(): String {
                        return "Doug"
                    }
                }
                fun main(): Unit {
                    println(User().announcement())
                }
                """);
        assertEquals("Hello Doug!", output.strip());
    }

    @Test
    public void compileErrorInAnInterfaceProgramSuppressesAllOutput() {
        String program = """
                interface Named {
                    fun name(): String
                }
                class User implements Named {
                }
                fun main(): Unit {
                    println("unreachable")
                }
                """;
        PolyglotException failure = assertThrows(PolyglotException.class, () -> run(program));
        assertTrue(failure.getMessage(), failure.getMessage().contains("SOLV-SEM-020"));
    }
}
