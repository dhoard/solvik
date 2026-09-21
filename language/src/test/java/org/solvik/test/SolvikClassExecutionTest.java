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
 * End-to-end Phase 6 execution tests: class declarations, construction, instance methods,
 * {@code this}, and property reads/writes run through the Truffle AST backend. Assertions observe
 * {@code print}/{@code println} output so the tests exercise real construction and dispatch.
 */
public final class SolvikClassExecutionTest {

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
    public void constructsObjectAndReadsPropertiesAndMethods() {
        assertThat(run("""
                class User {
                    val id: Integer
                    var name: String

                    User(id: Integer, name: String) {
                        this.id = id
                        this.name = name
                    }

                    func describe(): String {
                        return this.name
                    }
                }

                    val user = User(7, "Doug")
                    println(user.id)
                    println(user.name)
                    println(user.describe())
                """)).isEqualTo("7\nDoug\nDoug\n");
    }

    @Test
    public void declarationInitializersRunWithoutAConstructor() {
        assertThat(run("""
                class Counter {
                    var count: Integer = 0
                    val label: String = "c"

                    func increment(): Unit {
                        this.count = this.count + 1
                    }

                    func value(): Integer {
                        return this.count
                    }
                }

                    val counter = Counter()
                    counter.increment()
                    counter.increment()
                    println(counter.value())
                    println(counter.label)
                """)).isEqualTo("2\nc\n");
    }

    @Test
    public void mutablePropertiesCanBeWrittenAfterConstruction() {
        assertThat(run("""
                class Box {
                    var value: Integer

                    Box(start: Integer) {
                        this.value = start
                    }
                }

                    val box = Box(1)
                    box.value = box.value + 41
                    println(box.value)
                """)).isEqualTo("42\n");
    }

    @Test
    public void unqualifiedMethodCallDispatchesOnThis() {
        assertThat(run("""
                class Greeter {
                    val name: String

                    Greeter(name: String) {
                        this.name = name
                    }

                    func greeting(): String {
                        return "Hello " .. displayName()
                    }

                    func displayName(): String {
                        return this.name
                    }
                }

                    println(Greeter("Doug").greeting())
                """)).isEqualTo("Hello Doug\n");
    }

    @Test
    public void immutablePropertyAssignedInConstructorIsReadable() {
        assertThat(run("""
                class Point {
                    val x: Integer
                    val y: Integer

                    Point(x: Integer, y: Integer) {
                        this.x = x
                        this.y = y
                    }

                    func sum(): Integer {
                        return this.x + this.y
                    }
                }

                    println(Point(2, 3).sum())
                """)).isEqualTo("5\n");
    }

    @Test
    public void objectDisplaysAsItsClassName() {
        assertThat(run("""
                class Empty {
                    val x: Integer = 0
                }

                    println(Empty())
                """)).isEqualTo("Empty\n");
    }

    @Test
    public void objectsCompareByIdentity() {
        assertThat(run("""
                class Marker {
                    val id: Integer
                    Marker(id: Integer) {
                        this.id = id
                    }
                }

                    val a = Marker(1)
                    val b = a
                    val c = Marker(1)
                    println(a == b)
                    println(a == c)
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void initIsAnOrdinaryIdentifier() {
        assertThat(run("""
                class Engine {
                    var value: Integer = 0

                    Engine(init: Integer) {
                        this.value = init
                    }

                    func bump(): Integer {
                        val init = 5
                        this.value = this.value + init
                        return this.value
                    }
                }

                class Timer {
                    func init(): Integer {
                        return 7
                    }
                }

                class Slot {
                    var init: Integer = 3
                }

                    println(Engine(10).bump())
                    println(Timer().init())
                    println(Slot().init)
                """)).isEqualTo("15\n7\n3\n");
    }

    @Test
    public void compileErrorInAClassPreventsAllOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            assertThatExceptionOfType(PolyglotException.class).isThrownBy(() -> context.eval(build("""
                        println("before")

                    class Broken {
                        val value: Integer
                    }
                    """, "broken.sol")));
        }
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("");
    }
}
