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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
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
                """)).isEqualTo("Rex\n");
    }

    @Test
    public void overrideDispatchesVirtuallyThroughASupertypeVariable() {
        assertThat(run("""
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
                """)).isEqualTo("woof\n");
    }

    @Test
    public void virtualDispatchReachesAnOverrideFromAnInheritedMethod() {
        assertThat(run("""
                open class Animal {
                    open func speak(): String {
                        return "..."
                    }

                    func announce(): String {
                        return "I say " .. this.speak()
                    }
                }
                class Dog extends Animal {
                    override func speak(): String {
                        return "woof"
                    }
                }
                    println(Dog().announce())
                """)).isEqualTo("I say woof\n");
    }

    @Test
    public void superMethodCallRunsTheSuperclassImplementation() {
        assertThat(run("""
                open class Animal {
                    open func speak(): String {
                        return "..."
                    }
                }
                class Dog extends Animal {
                    override func speak(): String {
                        return super.speak() .. " woof"
                    }
                }
                    println(Dog().speak())
                """)).isEqualTo("... woof\n");
    }

    @Test
    public void explicitSuperConstructorRunsBeforeSubclassInitialization() {
        assertThat(run("""
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
                """)).isEqualTo("4\nRex\n");
    }

    @Test
    public void implicitSuperConstructorRunsForAZeroArgumentSuperclass() {
        assertThat(run("""
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
                """)).isEqualTo("animal\n");
    }

    @Test
    public void superPropertyReadsTheInheritedField() {
        assertThat(run("""
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
                """)).isEqualTo("Rex\n");
    }

    @Test
    public void declarationInitializersRunAfterTheSuperConstructor() {
        assertThat(run("""
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
                """)).isEqualTo("15\nRex\n");
    }
}
