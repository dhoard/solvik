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
                    func name(): String
                }
                class User implements Named {
                    val label: String

                    User(label: String) {
                        this.label = label
                    }

                    func name(): String {
                        return this.label
                    }
                }
                func greet(named: Named): String {
                    return named.name()
                }
                    println(greet(User("Doug")))
                """);
        assertThat(output.strip()).isEqualTo("Doug");
    }

    @Test
    public void defaultMethodRunsForAClassThatImplementsOnlyTheRequirement() {
        String output = run("""
                interface Named {
                    func name(): String

                    func greeting(): String {
                        return "Hello " .. name()
                    }
                }
                class User implements Named {
                    val label: String

                    User(label: String) {
                        this.label = label
                    }

                    func name(): String {
                        return this.label
                    }
                }
                    println(User("Doug").greeting())
                """);
        assertThat(output.strip()).isEqualTo("Hello Doug");
    }

    @Test
    public void defaultMethodDispatchesVirtuallyToTheConcreteRequirement() {
        String output = run("""
                interface Named {
                    func name(): String

                    func greeting(): String {
                        return "Hello " .. name()
                    }
                }
                open class Base implements Named {
                    func name(): String {
                        return "base"
                    }
                }
                class Derived implements Named {
                    func name(): String {
                        return "derived"
                    }
                }
                func shout(named: Named): String {
                    return named.greeting()
                }
                    println(shout(Base()))
                    println(shout(Derived()))
                """);
        assertThat(output.split("\n")[0].strip()).isEqualTo("Hello base");
        assertThat(output.split("\n")[1].strip()).isEqualTo("Hello derived");
    }

    @Test
    public void explicitMethodOverridesAnInheritedDefault() {
        String output = run("""
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
                    val named: Named = User()
                    println(named.greeting())
                """);
        assertThat(output.strip()).isEqualTo("Hi Doug");
    }

    @Test
    public void multipleInterfacesDispatchIndependently() {
        String output = run("""
                interface Named {
                    func name(): String
                }
                interface Aged {
                    func age(): Integer
                }
                class User implements Named, Aged {
                    func name(): String {
                        return "Doug"
                    }

                    func age(): Integer {
                        return 42
                    }
                }
                func describe(named: Named, aged: Aged): Unit {
                    print(named.name())
                    print(" ")
                    println(aged.age())
                }
                    val user = User()
                    describe(user, user)
                """);
        assertThat(output.strip()).isEqualTo("Doug 42");
    }

    @Test
    public void conflictingDefaultsResolvedExplicitlyRunTheClassMethod() {
        String output = run("""
                interface A {
                    func greet(): String {
                        return "a"
                    }
                }
                interface B {
                    func greet(): String {
                        return "b"
                    }
                }
                class C implements A, B {
                    func greet(): String {
                        return "c"
                    }
                }
                func viaA(a: A): String {
                    return a.greet()
                }
                func viaB(b: B): String {
                    return b.greet()
                }
                    val c = C()
                    println(viaA(c))
                    println(viaB(c))
                """);
        assertThat(output.split("\n")[0].strip()).isEqualTo("c");
        assertThat(output.split("\n")[1].strip()).isEqualTo("c");
    }

    @Test
    public void interfaceFromAnExtendedInterfaceIsAcceptedAsAParameterType() {
        String output = run("""
                interface Readable {
                    func read(): String
                }
                interface Writable {
                    func write(value: String): String
                }
                interface Stream extends Readable, Writable {
                    func copy(): String {
                        return write(read())
                    }
                }
                class Buffer implements Stream {
                    val contents: String

                    Buffer(contents: String) {
                        this.contents = contents
                    }

                    func read(): String {
                        return this.contents
                    }

                    func write(value: String): String {
                        return "wrote " .. value
                    }
                }
                func roundTrip(stream: Stream): String {
                    return stream.copy()
                }
                    println(roundTrip(Buffer("x")))
                """);
        assertThat(output.strip()).isEqualTo("wrote x");
    }

    @Test
    public void classInheritsConformanceAndDispatchesThroughTheSuperclassMethod() {
        String output = run("""
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
                func viaInterface(named: Named): String {
                    return named.name()
                }
                    println(viaInterface(Derived()))
                """);
        assertThat(output.strip()).isEqualTo("base");
    }

    @Test
    public void interfaceRequirementIsReachedThroughAnAnyTypedBuiltinArgument() {
        String output = run("""
                interface Named {
                    func name(): String
                }
                class User implements Named {
                    func name(): String {
                        return "Doug"
                    }
                }
                    val user = User()
                    print(user.name())
                    println("")
                """);
        assertThat(output.strip()).isEqualTo("Doug");
    }

    @Test
    public void interfaceMethodRunsInsideALoopOnAnInterfaceTypedLocal() {
        String output = run("""
                interface Counter {
                    func tick(value: Integer): Integer
                }
                class Doubler implements Counter {
                    func tick(value: Integer): Integer {
                        return value * 2
                    }
                }
                func total(counter: Counter, limit: Integer): Integer {
                    var sum = 0
                    for (var i = 0; i < limit; i = i + 1) {
                        sum = sum + counter.tick(i)
                    }
                    return sum
                }
                    val counter: Counter = Doubler()
                    println(total(counter, 4))
                """);
        assertThat(output.strip()).isEqualTo("12");
    }

    @Test
    public void aDefaultMethodMayCallAnotherDefaultMethod() {
        String output = run("""
                interface Named {
                    func name(): String

                    func greeting(): String {
                        return "Hello " .. name()
                    }

                    func announcement(): String {
                        return greeting() .. "!"
                    }
                }
                class User implements Named {
                    func name(): String {
                        return "Doug"
                    }
                }
                    println(User().announcement())
                """);
        assertThat(output.strip()).isEqualTo("Hello Doug!");
    }

    @Test
    public void compileErrorInAnInterfaceProgramSuppressesAllOutput() {
        String program = """
                interface Named {
                    func name(): String
                }
                class User implements Named {
                }
                    println("unreachable")
                """;
        PolyglotException failure = expectThrows(PolyglotException.class, () -> run(program));
        assertThat(failure.getMessage().contains("SOLV-SEM-020")).as(failure.getMessage()).isTrue();
    }
}
