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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;

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
        assertThat(run("""
                class mutable Animal {
                    var name: String

                    Animal(name: String) {
                        this.name = name
                    }

                    method describe(): String {
                        return this.name
                    }
                }
                class Dog extends Animal {
                    Dog() {
                        super("Rex")
                    }
                }
                    var dog: Dog = Dog()
                    println(dog.describe())


                """)).isEqualTo("Rex\n");
    }

    @Test
    public void overrideDispatchesVirtuallyThroughASupertypeVariable() {
        assertThat(run("""
                class mutable Animal {
                    method mutable speak(): String {
                        return "..."
                    }
                }
                class Dog extends Animal {
                    method override speak(): String {
                        return "woof"
                    }
                }
                    var animal: Animal = Dog()
                    println(animal.speak())


                """)).isEqualTo("woof\n");
    }

    @Test
    public void virtualDispatchReachesAnOverrideFromAnInheritedMethod() {
        assertThat(run("""
                class mutable Animal {
                    method mutable speak(): String {
                        return "..."
                    }

                    method announce(): String {
                        return "I say " .. this.speak()
                    }
                }
                class Dog extends Animal {
                    method override speak(): String {
                        return "woof"
                    }
                }
                    println(Dog().announce())


                """)).isEqualTo("I say woof\n");
    }

    @Test
    public void superMethodCallRunsTheSuperclassImplementation() {
        assertThat(run("""
                class mutable Animal {
                    method mutable speak(): String {
                        return "..."
                    }
                }
                class Dog extends Animal {
                    method override speak(): String {
                        return super.speak() .. " woof"
                    }
                }
                    println(Dog().speak())


                """)).isEqualTo("... woof\n");
    }

    @Test
    public void explicitSuperConstructorRunsBeforeSubclassInitialization() {
        assertThat(run("""
                class mutable Animal {
                    var legs: Integer

                    Animal(legs: Integer) {
                        this.legs = legs
                    }
                }
                class Dog extends Animal {
                    var name: String

                    Dog(name: String) {
                        super(4)
                        this.name = name
                    }
                }
                    var dog: Dog = Dog("Rex")
                    println(dog.legs)
                    println(dog.name)

                """)).isEqualTo("4\nRex\n");
    }

    @Test
    public void implicitSuperConstructorRunsForAZeroArgumentSuperclass() {
        assertThat(run("""
                class mutable Animal {
                    var kind: String

                    Animal() {
                        this.kind = "animal"
                    }
                }
                class Dog extends Animal {
                    Dog() {
                    }
                }
                    println(Dog().kind)

                """)).isEqualTo("animal\n");
    }

    @Test
    public void superPropertyReadsTheInheritedField() {
        assertThat(run("""
                class mutable Animal {
                    var name: String

                    Animal(name: String) {
                        this.name = name
                    }
                }
                class Dog extends Animal {
                    Dog() {
                        super("Rex")
                    }

                    method describe(): String {
                        return super.name
                    }
                }
                    println(Dog().describe())

                """)).isEqualTo("Rex\n");
    }

   @Test
    public void declarationInitializersRunAfterTheSuperConstructor() {
        assertThat(run("""
                class mutable Animal {
                    var mutable energy: Integer = 10
                }
                class Dog extends Animal {
                    var name: String = "Rex"
                }
                    var dog: Dog = Dog()
                    dog.energy = dog.energy + 5
                    println(dog.energy)
                    println(dog.name)

                """)).isEqualTo("15\nRex\n");
    }

    /**
     * {@code abstract} locks construction of the class it marks and nothing below it.
     *
     * <p>Constructing the abstract supertype is the one act the marker forbids, so the accepted arm
     * of this pair differs from the rejected one by a single name: {@code Shape()} versus
     * {@code Circle()}. That contrast is what makes the rejection mean "abstract classes are not
     * constructible" rather than "constructing anything in this program is broken".
     */
    @Test
    public void anAbstractClassIsNotConstructibleButItsSubtypeIs() {
        String shared = """
                class abstract Shape {
                    var sides: Integer

                    Shape(sides: Integer) {
                        this.sides = sides
                    }
                }
                class Circle extends Shape {
                    Circle() {
                        super(1)
                    }
                }

                """;
        assertThatExceptionOfType(PolyglotException.class)
                .isThrownBy(() -> run(shared + "    var s: Shape = Shape()\n    println(s.sides)\n"))
                .withMessageContaining("SOLV-SEM-028");
        assertThat(run(shared + "    var c: Circle = Circle()\n    println(c.sides)\n")).isEqualTo("1\n");
    }

    /**
     * An {@code abstract} supertype's constructor is the shared state its subclasses reach with
     * {@code super(...)}.
     *
     * <p>The constructor of an unconstructible class is not dead code, and this is the observable
     * proof: the field it initializes and the method that reads it both run, through a subclass. Were
     * the marker implemented as "strip the constructor", the {@code super(0)} call would fail to
     * resolve rather than printing {@code 0 / shape}.
     */
    @Test
    public void aSubclassReachesAnAbstractSuperclassConstructorThroughSuper() {
        assertThat(run("""
                class abstract Shape {
                    var sides: Integer
                    var label: String

                    Shape(sides: Integer, label: String) {
                        this.sides = sides
                        this.label = label
                    }

                    method describe(): String {
                        return this.label
                    }
                }
                class Circle extends Shape {
                    Circle() {
                        super(0, "shape")
                    }
                }
                    var c: Circle = Circle()
                    println(c.sides .. " / " .. c.describe())


                """)).isEqualTo("0 / shape\n");
    }
}
