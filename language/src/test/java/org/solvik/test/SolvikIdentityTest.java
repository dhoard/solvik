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
 * Execution tests for the reference-identity operators {@code ===} and {@code !==}
 * (docs/LANGUAGE_SPEC.md section 3): identity answers whether two values are the same guest
 * allocation, it never invokes {@code equals} or any other guest method, and {@code !==} is its
 * exact logical negation. Static analysis has already rejected every non-identity-bearing type, so
 * these programs exercise only class, interface, and mutable-collection allocations.
 */
public final class SolvikIdentityTest {

    @Test
    public void identityAnswersAliasesAndDistinctObjects() {
        assertThat(run("""
                    class Point {
                        val x: Integer

                        Point(x: Integer) {
                            this.x = x
                        }
                    }

                    val first = Point(1)
                    val second = Point(1)
                    val aliasPoint = first
                    println(first === second)
                    println(first === aliasPoint)
                    println(first !== second)
                    println(first !== aliasPoint)
                """)).isEqualTo("false\ntrue\ntrue\nfalse\n");
    }

    @Test
    public void identityIsUnaffectedByAnEqualsOverride() {
        // `==` consults the override; `===` must not, even though it has an observable side effect.
        assertThat(run("""
                    class Point {
                        val x: Integer

                        Point(x: Integer) {
                            this.x = x
                        }

                        override func equals(other: Any?): Boolean {
                            println("equals called")
                            return true
                        }
                    }

                    val a = Point(1)
                    val b = Point(2)
                    println(a === b)
                    println(a !== b)
                """)).isEqualTo("false\ntrue\n");
    }

    @Test
    public void identityThroughAnInterfaceReceiver() {
        assertThat(run("""
                    interface Identified {
                        func id(): Integer
                    }

                    class Item implements Identified {
                        val n: Integer

                        Item(n: Integer) {
                            this.n = n
                        }

                        func id(): Integer {
                            return this.n
                        }
                    }

                    val a: Identified = Item(1)
                    val b: Identified = a
                    println(a === b)
                    println(a === Item(1))
                    println(a !== Item(1))
                """)).isEqualTo("true\nfalse\ntrue\n");
    }

    @Test
    public void identityAcrossASuperclassAndSubclass() {
        assertThat(run("""
                    open class Base {
                    }

                    class Derived extends Base {
                    }

                    val base: Base = Derived()
                    val derived: Derived = Derived()
                    println(base === base)
                    println(base === derived)
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void identityOfParameterizedCollections() {
        assertThat(run("""
                    val a: List<Integer> = List(1, 2)
                    val b: List<Integer> = a
                    val c: List<Integer> = List(1, 2)
                    println(a === b)
                    println(a === c)
                    println(a !== c)
                """)).isEqualTo("true\nfalse\ntrue\n");
    }

    @Test
    public void identityOfParameterizedUserClasses() {
        assertThat(run("""
                    class Box<T> {
                        val value: T

                        Box(value: T) {
                            this.value = value
                        }
                    }

                    val a: Box<Integer> = Box(1)
                    val b: Box<Integer> = a
                    val c: Box<Integer> = Box(1)
                    println(a === b)
                    println(a === c)
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void identityOfNullableOperandsAndNull() {
        assertThat(run("""
                    class Point {
                    }

                    val missing: Point? = null
                    val present: Point? = Point()
                    println(missing === null)
                    println(present === null)
                    println(missing !== null)
                    println(present !== null)
                    println(present === missing)
                """)).isEqualTo("true\nfalse\nfalse\ntrue\nfalse\n");
    }

    @Test
    public void identityEvaluatesEachOperandExactlyOnce() {
        assertThat(run("""
                    class Item {
                    }

                    class Counter {
                        var calls: Integer

                        Counter() {
                            this.calls = 0
                        }
                    }

                    func make(counter: Counter): Item {
                        counter.calls = counter.calls + 1
                        return Item()
                    }

                    val counter = Counter()
                    println(make(counter) === make(counter))
                    println(counter.calls)
                """)).isEqualTo("false\n2\n");
    }

    @Test
    public void identityNegationEvaluatesEachOperandExactlyOnce() {
        assertThat(run("""
                    class Item {
                    }

                    class Counter {
                        var calls: Integer

                        Counter() {
                            this.calls = 0
                        }
                    }

                    func make(counter: Counter): Item {
                        counter.calls = counter.calls + 1
                        return Item()
                    }

                    val counter = Counter()
                    println(make(counter) !== make(counter))
                    println(counter.calls)
                """)).isEqualTo("true\n2\n");
    }

    @Test
    public void identityOfEachMutableCollectionType() {
        assertThat(run("""
                    val s1: Set<Integer> = Set(1)
                    val s2: Set<Integer> = s1
                    val s3: Set<Integer> = Set(1)
                    println(s1 === s2)
                    println(s1 === s3)

                    val m1: Map<Integer, Integer> = Map(1: 1)
                    val m2: Map<Integer, Integer> = m1
                    val m3: Map<Integer, Integer> = Map(1: 1)
                    println(m1 === m2)
                    println(m1 === m3)

                    val st1: Stack<Integer> = Stack(1)
                    val st2: Stack<Integer> = st1
                    val st3: Stack<Integer> = Stack(1)
                    println(st1 === st2)
                    println(st1 === st3)
                """)).isEqualTo("true\nfalse\ntrue\nfalse\ntrue\nfalse\n");
    }

    @Test
    public void identityWithNullOnEitherSide() {
        assertThat(run("""
                    class Point {
                    }

                    val missing: Point? = null
                    val present: Point? = Point()
                    println(null !== missing)
                    println(null === missing)
                    println(null === present)
                    println(null !== present)
                """)).isEqualTo("false\ntrue\nfalse\ntrue\n");
    }

    @Test
    public void notEqualsEqualsRendersBooleanForAliasesAndCollections() {
        assertThat(run("""
                    class Box {
                    }

                    val first = Box()
                    val second = Box()
                    val dup = first

                    val a: List<Integer> = List(1, 2)
                    val b: List<Integer> = a
                    val c: List<Integer> = List(1, 2)

                    println(first !== second)
                    println(first !== dup)
                    println(a !== c)
                    println(a !== b)
                """)).isEqualTo("true\nfalse\ntrue\nfalse\n");
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
            return Source.newBuilder("solvik", source, "identity.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
