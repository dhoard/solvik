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
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.solvik.truffle.SolvikUnit;
import org.solvik.truffle.object.SolvikRuntimeTypes;
import org.solvik.type.AnyType;

/**
 * Runtime {@code is} and {@code as} behavior (docs/LANGUAGE_SPEC.md section 18). A type test uses
 * the runtime representation, so every built-in scalar must keep its declared representation: a
 * {@code Float} literal is a runtime {@code Float}, not a widened {@code Double}. {@code Number}
 * matches every numeric representation, value types and nominal types are distinguished, and an
 * unsuccessful cast raises a Solvik runtime type error.
 */
public final class SolvikTypeTestRuntimeTest {

    @Test
    public void anyRuntimeCheckAcceptsEveryNonNullKindAndRejectsNull() {
        assertThat(SolvikRuntimeTypes.isInstance(null, AnyType.INSTANCE, null)).as("null is never an instance of non-null Any").isFalse();
        assertThat(SolvikRuntimeTypes.isInstance(1, AnyType.INSTANCE, null)).isTrue();
        assertThat(SolvikRuntimeTypes.isInstance("x", AnyType.INSTANCE, null)).isTrue();
        assertThat(SolvikRuntimeTypes.isInstance(SolvikUnit.INSTANCE, AnyType.INSTANCE, null)).isTrue();
    }

    @Test
    public void anyTestCoversCollectionsEnumsRegexAndUserInstances() {
        assertThat(run("""
                    interface Named {
                        func name(): String
                    }
                    class User implements Named {
                        func name(): String {
                            return "doug"
                        }
                    }
                    enum Color {
                        Red
                    }
                    func noop() {
                    }
                    val values: Any = List<Integer>(1, 2)
                    val pattern: Any = Regex("a")
                    val found: Any? = Regex("a").find("a")
                    val color: Any = Color.Red
                    val user: Any = User()
                    val unit: Any = noop()
                    println(values is Any)
                    println(pattern is Any)
                    if (found != null) {
                        println(found is Any)
                    }
                    println(color is Any)
                    println(user is Any)
                    println(unit is Any)
                """)).isEqualTo("true\ntrue\ntrue\ntrue\ntrue\ntrue\n");
    }

    @Test
    public void castToAnyKeepsTheRuntimeValue() {
        assertThat(run("""
                    val n: Any = 1 as Any
                    val s: Any = "x" as Any
                    println(n is Integer)
                    println(n is Any)
                    println(s is String)
                    println(s is Any)
                """)).isEqualTo("true\ntrue\ntrue\ntrue\n");
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({
            "Byte(1), Byte",
            "Short(1), Short",
            "1, Integer",
            "1L, Long",
            "1.5f, Float",
            "1.5, Double",
            "true, Boolean",
            "\"s\", String",
    })
    public void typeTestMatchesTheRuntimeRepresentation(String value, String typeName) {
        String source = "func f(v: Any): Boolean {\n    return v is " + typeName + "\n}\nprintln(f(" + value + "))\n";
        assertThat(run(source)).as("value %s must be a runtime %s", value, typeName).isEqualTo("true\n");
    }

    @Test
    public void characterTypeTestMatchesTheRuntimeRepresentation() {
        assertThat(run("""
                    func f(v: Any): Boolean {
                        return v is Character
                    }

                    println(f('a'))
                    println(f("a"))
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void floatLiteralDoesNotWidenToDouble() {
        assertThat(run("""
                    println(1.5f is Float)
                    println(1.5f is Double)
                    println(1.5 is Double)
                    println(1.5 is Float)
                """)).isEqualTo("true\nfalse\ntrue\nfalse\n");
    }

    @Test
    public void floatParameterAndLocalKeepTheirRuntimeRepresentation() {
        assertThat(run("""
                    func f(v: Float): Boolean {
                        return v is Float
                    }

                    val g: Float = 1.5f
                    println(f(1.5f))
                    println(g is Float)
                    println(g is Double)
                """)).isEqualTo("true\ntrue\nfalse\n");
    }

    @Test
    public void numberMatchesEveryNumericRepresentation() {
        assertThat(run("""
                    func f(v: Any): Boolean {
                        return v is Number
                    }

                    println(f(Byte(1)))
                    println(f(Short(1)))
                    println(f(1))
                    println(f(1L))
                    println(f(1.5f))
                    println(f(1.5))
                    println(f("x"))
                    println(f(true))
                """)).isEqualTo("true\ntrue\ntrue\ntrue\ntrue\ntrue\nfalse\nfalse\n");
    }

    @Test
    public void anyMatchesEveryNonNullValue() {
        assertThat(run("""
                    println(1 is Any)
                    println("x" is Any)
                    println(true is Any)
                    println(1L is Any)
                    println(1.5 is Any)
                    println('c' is Any)
                """)).isEqualTo("true\ntrue\ntrue\ntrue\ntrue\ntrue\n");
    }

    @Test
    public void unitRegexAndRegexMatchTypeTestsExecute() {
        assertThat(run("""
                    func noop() {
                    }

                    val unit: Any = noop()
                    println(unit is Unit)
                    val pattern: Any = Regex(r"a")
                    println(pattern is Regex)
                    println(pattern is String)
                    val found = Regex(r"a").find("a")
                    if (found != null) {
                        println(found is RegexMatch)
                    }
                """)).isEqualTo("true\ntrue\nfalse\ntrue\n");
    }

    @Test
    public void classInterfaceAndEnumTypeTestsExecute() {
        assertThat(run("""
                    interface Named {
                        func name(): String
                    }

                    open class Animal {
                    }

                    class Dog extends Animal implements Named {
                        func name(): String {
                            return "dog"
                        }
                    }

                    enum Color {
                        Red
                        Green
                    }

                    val dog: Animal = Dog()
                    println(dog is Dog)
                    println(dog is Animal)
                    println(dog is Named)
                    println(dog is Any)
                    val color: Any = Color.Red
                    println(color is Color)
                    println(color is Animal)
                """)).isEqualTo("true\ntrue\ntrue\ntrue\ntrue\nfalse\n");
    }

    @Test
    public void typeTestsWalkAMultiLevelInheritanceChain() {
        assertThat(run("""
                    open class A {
                    }

                    open class B extends A {
                    }

                    class C extends B {
                    }

                    val value: A = C()
                    println(value is A)
                    println(value is B)
                    println(value is C)
                """)).isEqualTo("true\ntrue\ntrue\n");
    }

    @Test
    public void successfulCastToNumberKeepsTheRuntimeType() {
        assertThat(run("""
                    func asNumber(v: Any): Number {
                        return v as Number
                    }

                    println(asNumber(1) is Integer)
                    println(asNumber(1.5f) is Float)
                """)).isEqualTo("true\ntrue\n");
    }

    @Test
    public void castToAnInterfaceTypeKeepsTheRuntimeValue() {
        // Confirms the SolvikConvertNode/`as` path for an interface target: the runtime value is
        // preserved and the check verifies the receiver implements the interface.
        assertThat(run("""
                    interface Named {
                        func name(): String
                    }

                    class User implements Named {
                        func name(): String {
                            return "doug"
                        }
                    }

                    val obj: Any = User()
                    val named = obj as Named
                    println(named.name())
                """)).isEqualTo("doug\n");
    }

    @Test
    public void typeTestOnAnErasedGenericArgumentIsRejectedAtCompileTime() {
        // Confirms that `is` against a generic type with erased type arguments is rejected at
        // compile time (SOLV-TYPE-031): runtime type tests require reifiable types, so
        // `List<Integer>` cannot be inspected through `is` at runtime.
        PolyglotException failure = failureOf("val list: List<Integer> = List(1, 2)\nprintln(list is List<Integer>)\n");
        assertThat(failure.isSyntaxError()).as(failure.getMessage()).isTrue();
        assertThat(failure.getMessage()).contains("SOLV-TYPE-031");
    }

    @Test
    public void unsuccessfulCastRaisesARuntimeTypeError() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build("""
                        func asNumber(v: Any): Number {
                            return v as Number
                        }

                        println(asNumber(true))
                    """));
            throw new AssertionError("expected the cast to fail");
        } catch (PolyglotException e) {
            assertThat(e.isSyntaxError()).as(e.getMessage()).isFalse();
            assertThat(e.isGuestException()).as("a guest exception is reported").isTrue();
            assertThat(e.getMessage()).contains("type");
        }
    }

    private static PolyglotException failureOf(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source));
        } catch (PolyglotException e) {
            return e;
        }
        throw new AssertionError("expected a failure but the program completed: " + source);
    }

    private static String run(String source) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(source));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Source build(String source) {
        try {
            return Source.newBuilder("solvik", source, "typetest.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
