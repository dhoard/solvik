/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Test;

/**
 * End-to-end Phase 7 execution tests: single inheritance, inherited properties and methods,
 * overrides with virtual dispatch, and explicit or implicit {@code super} initializer calls run
 * through the Truffle AST backend.
 */
public final class SolvikInheritanceExecutionTest {

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
    public void inheritedPropertyAndMethodAreAvailable() {
        assertEquals("Rex\n", run("""
                open class Animal {
                    val name: String

                    Animal(name: String) {
                        this.name = name
                    }

                    func describe(): String {
                        return this.name
                    }
                }
                class Dog extends Animal {
                    Dog() {
                        super("Rex")
                    }
                }
                    val dog = Dog()
                    println(dog.describe())
                """));
    }

    @Test
    public void overrideDispatchesVirtuallyThroughASupertypeVariable() {
        assertEquals("woof\n", run("""
                open class Animal {
                    open func speak(): String {
                        return "..."
                    }
                }
                class Dog extends Animal {
                    override func speak(): String {
                        return "woof"
                    }
                }
                    val animal: Animal = Dog()
                    println(animal.speak())
                """));
    }

    @Test
    public void virtualDispatchReachesAnOverrideFromAnInheritedMethod() {
        assertEquals("I say woof\n", run("""
                open class Animal {
                    open func speak(): String {
                        return "..."
                    }

                    func announce(): String {
                        return "I say " + this.speak()
                    }
                }
                class Dog extends Animal {
                    override func speak(): String {
                        return "woof"
                    }
                }
                    println(Dog().announce())
                """));
    }

    @Test
    public void superMethodCallRunsTheSuperclassImplementation() {
        assertEquals("... woof\n", run("""
                open class Animal {
                    open func speak(): String {
                        return "..."
                    }
                }
                class Dog extends Animal {
                    override func speak(): String {
                        return super.speak() + " woof"
                    }
                }
                    println(Dog().speak())
                """));
    }

    @Test
    public void explicitSuperConstructorRunsBeforeSubclassInitialization() {
        assertEquals("4\nRex\n", run("""
                open class Animal {
                    val legs: Int

                    Animal(legs: Int) {
                        this.legs = legs
                    }
                }
                class Dog extends Animal {
                    val name: String

                    Dog(name: String) {
                        super(4)
                        this.name = name
                    }
                }
                    val dog = Dog("Rex")
                    println(dog.legs)
                    println(dog.name)
                """));
    }

    @Test
    public void implicitSuperConstructorRunsForAZeroArgumentSuperclass() {
        assertEquals("animal\n", run("""
                open class Animal {
                    val kind: String

                    Animal() {
                        this.kind = "animal"
                    }
                }
                class Dog extends Animal {
                    Dog() {
                    }
                }
                    println(Dog().kind)
                """));
    }

    @Test
    public void superPropertyReadsTheInheritedField() {
        assertEquals("Rex\n", run("""
                open class Animal {
                    val name: String

                    Animal(name: String) {
                        this.name = name
                    }
                }
                class Dog extends Animal {
                    Dog() {
                        super("Rex")
                    }

                    func describe(): String {
                        return super.name
                    }
                }
                    println(Dog().describe())
                """));
    }

    @Test
    public void declarationInitializersRunAfterTheSuperConstructor() {
        assertEquals("15\nRex\n", run("""
                open class Animal {
                    var energy: Int = 10
                }
                class Dog extends Animal {
                    val name: String = "Rex"
                }
                    val dog = Dog()
                    dog.energy = dog.energy + 5
                    println(dog.energy)
                    println(dog.name)
                """));
    }
}
