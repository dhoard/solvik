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
                    var id: Integer
                    var mutable name: String

                    User(id: Integer, name: String) {
                        this.id = id
                        this.name = name
                    }

                    method describe(): String {
                        return this.name
                    }
                }

                    var user: User = User(7, "Doug")
                    println(user.id)
                    println(user.name)
                    println(user.describe())

                """)).isEqualTo("7\nDoug\nDoug\n");
    }

    @Test
    public void declarationInitializersRunWithoutAConstructor() {
        assertThat(run("""
                class Counter {
                    var mutable count: Integer = 0
                    var label: String = "c"

                    method increment() {
                        this.count = this.count + 1
                    }

                    method value(): Integer {
                        return this.count
                    }
                }

                    var counter: Counter = Counter()
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
                    var mutable value: Integer

                    Box(start: Integer) {
                        this.value = start
                    }
                }

                    var box: Box = Box(1)
                    box.value = box.value + 41
                    println(box.value)

                """)).isEqualTo("42\n");
    }

    @Test
    public void unqualifiedMethodCallDispatchesOnThis() {
        assertThat(run("""
                class Greeter {
                    var name: String

                    Greeter(name: String) {
                        this.name = name
                    }

                    method greeting(): String {
                        return "Hello " .. displayName()
                    }

                    method displayName(): String {
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
                    var x: Integer
                    var y: Integer

                    Point(x: Integer, y: Integer) {
                        this.x = x
                        this.y = y
                    }

                    method sum(): Integer {
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
                    var x: Integer = 0
                }

                    println(Empty())
                """)).isEqualTo("Empty\n");
    }

    @Test
    public void objectsCompareByIdentity() {
        assertThat(run("""
                class Marker {
                    var id: Integer
                    Marker(id: Integer) {
                        this.id = id
                    }
                }

                    var a: Marker = Marker(1)
                    var b: Marker = a
                    var c: Marker = Marker(1)
                    println(a == b)
                    println(a == c)

                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void initIsAnOrdinaryIdentifier() {
        assertThat(run("""
                class Engine {
                    var mutable value: Integer = 0

                    Engine(init: Integer) {
                        this.value = init
                    }

                    method bump(): Integer {
                        var init: Integer = 5
                        this.value = this.value + init
                        return this.value
                    }
                }

                class Timer {
                    method init(): Integer {
                        return 7
                    }
                }

                class Slot {
                    var mutable init: Integer = 3
                }

                    println(Engine(10).bump())
                    println(Timer().init())
                    println(Slot().init)

                """)).isEqualTo("15\n7\n3\n");
    }

    @Test
    public void aLocalShadowsAPropertyOfTheSameNameAndThisReachesTheProperty() {
        // docs/LANGUAGE_SPEC.md section 7: a bare name is never a property, so a local that reuses a
        // property name denotes the local, and `this.name` stays the only route to the property. This
        // pins both halves at once: the local value and the property value are observed separately, and
        // the property write inside the method is the qualified one.
        assertThat(run("""
                class Counter {
                    var mutable total: Integer = 0

                    method add(amount: Integer): Integer {
                        var total: Integer = amount * 2
                        this.total = this.total + amount
                        return total
                    }

                    method sum(): Integer {
                        return this.total
                    }
                }

                var mutable counter: Counter = Counter()
                println(counter.add(4))
                println(counter.sum())
                println(counter.add(1))
                println(counter.sum())

                """)).isEqualTo("8\n4\n2\n5\n");
    }

    @Test
    public void collectionAndMapPropertiesAreReachableThroughThis() {
        // Generic instantiated collection types are usable as property types, and a collection-typed
        // property is mutated through `this` exactly like a scalar one.
        assertThat(run("""
                class Bag {
                    var items: Set<Integer> = Set<Integer>()
                    var counts: Map<String, Integer> = Map<String, Integer>()

                    method add(value: Integer) {
                        this.items.add(value)
                    }

                    method hit(key: String): Integer {
                        var mutable next: Integer = 1
                        if (this.counts.containsKey(key)) {
                            next = this.counts.get(key) + 1
                        }
                        this.counts.put(key, next)
                        return next
                    }

                    method size(): Integer {
                        return this.items.size
                    }
                }

                var mutable bag: Bag = Bag()
                bag.add(1)
                bag.add(2)
                bag.add(1)
                println(bag.size())
                println(bag.hit("x"))
                println(bag.hit("x"))
                println(bag.hit("y"))

                """)).isEqualTo("2\n1\n2\n1\n");
    }

    @Test
    public void inheritedPropertiesAreReachableThroughThisInASubclass() {
        assertThat(run("""
                class mutable Base {
                    var id: Integer = 5
                }

                class Derived extends Base {
                    var mutable extra: Integer = 2

                    method show(): Integer {
                        return this.id + this.extra
                    }
                }

                println(Derived().show())

                """)).isEqualTo("7\n");
    }

    @Test
    public void varLocalWriteLoweringUpdatesTheFrameSlotAtRuntime() {
        // Confirms the SolvikWriteLocalVariableNode path for a non-Integer local variable:
        // `var x = 1` then `x = 2` must produce 2, exercising the lowering-time slot reuse.
        assertThat(run("""
                func tally(): Integer {
                    var mutable counter: Integer = 5
                    counter = counter + 1
                    counter = counter * 7
                    return counter
                }
                println(tally())

                """)).isEqualTo("42\n");
    }

    @Test
    public void compileErrorInAClassPreventsAllOutput() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            assertThatExceptionOfType(PolyglotException.class).isThrownBy(() -> context.eval(build("""
                        println("before")

                    class Broken {
                        var value: Integer
                    }
                    """, "broken.sol")));
        }
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("");
    }
}
