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
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Execution tests for the single Solvik equality rule (docs/LANGUAGE_SPEC.md section 3): built-in
 * scalars and enum values compare by value, ordinary class instances compare by identity, and the
 * same definition drives {@code Set} and {@code Map} membership. Every built-in scalar branch of
 * {@code SolvikValues.equal} is exercised directly so the rule cannot silently diverge from Java
 * {@code equals}.
 */
public final class SolvikEqualityTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "Byte(1) == Byte(1), true",
            "Byte(1) != Byte(2), true",
            "Short(1) == Short(1), true",
            "Short(1) != Short(2), true",
            "1 == 1, true",
            "1 != 2, true",
            "1L == 1L, true",
            "1L != 2L, true",
            "1.5f == 1.5f, true",
            "1.5f != 2.5f, true",
            "1.5 == 1.5, true",
            "1.5 != 2.5, true",
            "true == true, true",
            "true != false, true",
            "\"a\" == \"a\", true",
            "\"a\" != \"b\", true",
    })
    public void scalarEqualityComparesByValue(String expression, boolean expected) {
        assertThat(run("    println(" + expression + ")\n")).isEqualTo(expected + "\n");
    }

    @Test
    public void characterEqualityComparesByValue() {
        assertThat(run("""
                    println('A' == 'A')
                    println('A' != 'B')
                """)).isEqualTo("true\ntrue\n");
    }

    @Test
    public void anyOperandsCompareAcrossEveryRuntimeRepresentation() {
        assertThat(run("""
                    enum Color {
                        Red
                        Blue
                    }

                    func cmp(a: Any, b: Any): Boolean {
                        return a == b
                    }

                    println(cmp(1, 1))
                    println(cmp("x", "x"))
                    println(cmp(1, "x"))
                    println(cmp("x", 1))
                    println(cmp(true, true))
                    println(cmp(true, 1))
                    println(cmp(1L, 1L))
                    println(cmp(1.5, 1.5))
                    println(cmp(1.5f, 1.5f))
                    println(cmp(Color.Red, Color.Red))
                    println(cmp(Color.Red, Color.Blue))
                """)).isEqualTo("true\ntrue\nfalse\nfalse\ntrue\nfalse\ntrue\ntrue\ntrue\ntrue\nfalse\n");
    }

    @Test
    public void anyOperandsCompareByIdentityAndValue() {
        assertThat(run("""
                    class Box {
                    }

                    func cmp(a: Any, b: Any): Boolean {
                        return a == b
                    }

                    val first = Box()
                    val second = Box()
                    println(cmp(first, first))
                    println(cmp(first, second))
                    println(cmp(1, 1))
                    println(cmp("x", "x"))
                """)).isEqualTo("true\nfalse\ntrue\ntrue\n");
    }

    @Test
    public void ordinaryClassInstancesCompareByIdentity() {
        assertThat(run("""
                    class Box {
                    }

                    val a = Box()
                    val b = Box()
                    println(a == a)
                    println(a == b)
                    println(a != b)
                """)).isEqualTo("true\nfalse\ntrue\n");
    }

    @Test
    public void enumValuesCompareByValue() {
        assertThat(run("""
                    enum Color {
                        Red
                        Green
                    }

                    val red = Color.Red
                    val otherRed = Color.Red
                    val green = Color.Green
                    println(red == otherRed)
                    println(red == green)
                    println(red != green)
                """)).isEqualTo("true\nfalse\ntrue\n");
    }

    @Test
    public void enumValueWithPayloadComparesByValue() {
        assertThat(run("""
                    enum Result {
                        Ok(Integer)
                        Error(String)
                    }

                    val first = Result.Ok(1)
                    val same = Result.Ok(1)
                    val different = Result.Ok(2)
                    println(first == same)
                    println(first == different)
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void setMembershipUsesScalarValueEquality() {
        assertThat(run("""
                    var longs: Set<Long> = Set(1L, 2L)
                    println(longs.contains(1L))
                    println(longs.contains(3L))
                    var doubles: Set<Double> = Set(1.5, 2.5)
                    println(doubles.contains(2.5))
                    var chars: Set<Character> = Set('a', 'b')
                    println(chars.contains('b'))
                """)).isEqualTo("true\nfalse\ntrue\ntrue\n");
    }

    @Test
    public void mapLookupUsesScalarValueEquality() {
        assertThat(run("""
                    var byLong: Map<Long, String> = Map(1L: "one")
                    println(byLong.containsKey(1L))
                    println(byLong.containsKey(2L))
                    var byDouble: Map<Double, Integer> = Map(1.5: 1)
                    println(byDouble.containsKey(1.5))
                """)).isEqualTo("true\nfalse\ntrue\n");
    }

    @Test
    public void setMembershipUsesEnumValueEquality() {
        assertThat(run("""
                    enum Color {
                        Red
                        Green
                    }

                    var colors: Set<Color> = Set(Color.Red)
                    println(colors.contains(Color.Red))
                    println(colors.contains(Color.Green))
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void mapKeysUseEnumValueEquality() {
        assertThat(run("""
                    enum Color {
                        Red
                        Green
                    }

                    var names: Map<Color, String> = Map(Color.Red: "red")
                    println(names.containsKey(Color.Red))
                    println(names.containsKey(Color.Green))
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void setKeepsOnlyTheFirstOfTwoEqualValues() {
        assertThat(run("""
                    var values: Set<Long> = Set(1L, 1L, 2L)
                    println(values.size)
                """)).isEqualTo("2\n");
    }

    @Test
    public void userEqualsOverrideDispatchesFromOperatorAndExplicitCall() {
        assertThat(run("""
                    class Point {
                        val x: Integer

                        Point(x: Integer) {
                            this.x = x
                        }

                        override func equals(other: Any?): Boolean {
                            if (other is Point) {
                                return this.x == other.x
                            }
                            return false
                        }
                        override func hashCode(): Integer {
                            return this.x
                        }
                    }

                    val a = Point(1)
                    val b = Point(1)
                    val c = Point(2)
                    println(a == b)
                    println(a.equals(b))
                    println(a != c)
                    println(a.equals(c))
                    println(a === b)
                """)).isEqualTo("true\ntrue\ntrue\nfalse\nfalse\n");
    }

    @Test
    public void inheritedEqualsOverrideRemainsEffective() {
        assertThat(run("""
                    open class Tagged {
                        override func equals(other: Any?): Boolean {
                            return other is Tagged
                        }
                        override func hashCode(): Integer {
                            return 1
                        }
                    }

                    class Item extends Tagged {
                    }

                    val a = Item()
                    val b = Item()
                    println(a == b)
                    println(a.equals(b))
                """)).isEqualTo("true\ntrue\n");
    }

    @Test
    public void nullComparisonNeverInvokesTheOverride() {
        assertThat(run("""
                    class Point {
                        override func equals(other: Any?): Boolean {
                            println("equals called")
                            return true
                        }
                        override func hashCode(): Integer {
                            return 1
                        }
                    }

                    val p = Point()
                    val maybe: Point? = p
                    println(maybe == null)
                    println(null == maybe)
                    println(maybe != null)
                    println(p.equals(null))
                """)).isEqualTo("false\nfalse\ntrue\nfalse\n");
    }

    @Test
    public void safeExplicitEqualsOnANullableReceiver() {
        assertThat(run("""
                    class Point {
                        override func equals(other: Any?): Boolean {
                            return true
                        }
                        override func hashCode(): Integer {
                            return 1
                        }
                    }

                    val missing: Point? = null
                    val present: Point? = Point()
                    println(missing?.equals(Point()))
                    println(present?.equals(Point()))
                """)).isEqualTo("null\ntrue\n");
    }

    @Test
    public void collectionMembershipUsesTheUserOverride() {
        assertThat(run("""
                    class Point {
                        val x: Integer

                        Point(x: Integer) {
                            this.x = x
                        }

                        override func equals(other: Any?): Boolean {
                            if (other is Point) {
                                return this.x == other.x
                            }
                            return false
                        }
                        override func hashCode(): Integer {
                            return this.x
                        }
                    }

                    var points: Set<Point> = Set(Point(1))
                    println(points.contains(Point(1)))
                    println(points.contains(Point(2)))
                    var byPoint: Map<Point, String> = Map(Point(1): "one")
                    println(byPoint.get(Point(1)))
                """)).isEqualTo("true\nfalse\none\n");
    }

    @Test
    public void superEqualsReachesTheSuperclassOverride() {
        assertThat(run("""
                    open class Base {
                        val id: Integer

                        Base(id: Integer) {
                            this.id = id
                        }

                        open override func equals(other: Any?): Boolean {
                            if (other is Base) {
                                return this.id == other.id
                            }
                            return false
                        }
                        open override func hashCode(): Integer {
                            return this.id
                        }
                    }

                    class Derived extends Base {
                        val extra: Integer

                        Derived(id: Integer, extra: Integer) {
                            super(id)
                            this.extra = extra
                        }

                        override func equals(other: Any?): Boolean {
                            if (other is Derived) {
                                return super.equals(other) && this.extra == other.extra
                            }
                            return false
                        }
                        override func hashCode(): Integer {
                            return 31 * super.hashCode() + this.extra
                        }
                    }

                    println(Derived(1, 2) == Derived(1, 2))
                    println(Derived(1, 2) == Derived(1, 3))
                    println(Derived(1, 2) == Derived(9, 2))
                """)).isEqualTo("true\nfalse\nfalse\n");
    }

    @Test
    public void superEqualsReachesTheRootIdentityDefault() {
        assertThat(run("""
                    open class Base {
                    }

                    class Derived extends Base {
                        override func equals(other: Any?): Boolean {
                            return super.equals(other)
                        }
                        override func hashCode(): Integer {
                            return super.hashCode()
                        }
                    }

                    val a = Derived()
                    val b = Derived()
                    println(a == a)
                    println(a == b)
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void regexValuesCompareBySourceTextNotCaching() {
        assertThat(run("""
                    val constant = Regex("a+")
                    val dynamic = Regex("a" .. "+")
                    val other = Regex("b+")
                    println(constant == dynamic)
                    println(constant.equals(dynamic))
                    println(constant == other)
                """)).isEqualTo("true\ntrue\nfalse\n");
    }

    @Test
    public void regexMatchComparesItsImmutableSnapshot() {
        assertThat(run("""
                    val pattern = Regex("(a)(b)?")
                    val first = pattern.find("a")
                    val second = pattern.find("a")
                    val third = pattern.find("ab")
                    println(first == second)
                    println(first == third)
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void enumPayloadRecursesThroughAUserOverride() {
        assertThat(run("""
                    class Point {
                        val x: Integer

                        Point(x: Integer) {
                            this.x = x
                        }

                        override func equals(other: Any?): Boolean {
                            if (other is Point) {
                                return this.x == other.x
                            }
                            return false
                        }
                        override func hashCode(): Integer {
                            return this.x
                        }
                    }

                    enum Wrapper {
                        Wrap(Point)
                    }

                    println(Wrapper.Wrap(Point(1)) == Wrapper.Wrap(Point(1)))
                    println(Wrapper.Wrap(Point(1)) == Wrapper.Wrap(Point(2)))
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void enumComparedWithANonEnumKindIsUnequal() {
        assertThat(run("""
                    enum Color {
                        Red
                        Blue
                    }

                    func cmp(a: Any, b: Any): Boolean {
                        return a == b
                    }

                    println(cmp(Color.Red, 1))
                    println(cmp(1, Color.Red))
                    println(cmp(Color.Red, "x"))
                """)).isEqualTo("false\nfalse\nfalse\n");
    }

    @Test
    public void enumPayloadSameReferenceStillDispatchesTheOverride() {
        // The override has an observable side effect, so a same-reference payload must still call it:
        // there is no identity shortcut before user dispatch.
        assertThat(run("""
                    class Point {
                        override func equals(other: Any?): Boolean {
                            println("equals called")
                            return true
                        }
                        override func hashCode(): Integer {
                            return 1
                        }
                    }

                    enum Wrapper {
                        Wrap(Point)
                    }

                    val p = Point()
                    println(Wrapper.Wrap(p) == Wrapper.Wrap(p))
                """)).isEqualTo("equals called\ntrue\n");
    }

    @Test
    public void floatingEqualityPreservesIeeeBehaviour() {
        assertThat(run("""
                    val nan = 0.0 / 0.0
                    println(nan == nan)
                    println(nan != nan)
                    println(0.0 == -0.0)
                    println(1.0 / 0.0 == 1.0 / 0.0)
                    println(1.0 / 0.0 != -1.0 / 0.0)
                """)).isEqualTo("false\ntrue\ntrue\ntrue\ntrue\n");
    }

    @Test
    public void enumEqualityHandlesZeroOneAndMultiplePayloads() {
        assertThat(run("""
                    enum Shape {
                        Empty
                        Dot(Integer)
                        Pair(Integer, Integer)
                    }

                    println(Shape.Empty == Shape.Empty)
                    println(Shape.Dot(1) == Shape.Dot(1))
                    println(Shape.Dot(1) == Shape.Dot(2))
                    println(Shape.Pair(1, 2) == Shape.Pair(1, 2))
                    println(Shape.Pair(1, 2) == Shape.Pair(1, 3))
                """)).isEqualTo("true\ntrue\nfalse\ntrue\nfalse\n");
    }

    @Test
    public void nestedEnumPayloadsCompareRecursively() {
        assertThat(run("""
                    enum Inner {
                        N(Integer)
                    }

                    enum Outer {
                        Wrap(Inner)
                    }

                    println(Outer.Wrap(Inner.N(1)) == Outer.Wrap(Inner.N(1)))
                    println(Outer.Wrap(Inner.N(1)) == Outer.Wrap(Inner.N(2)))
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void nullableEnumPayloadsUseTheNullRule() {
        assertThat(run("""
                    enum Maybe {
                        Some(Integer?)
                    }

                    println(Maybe.Some(null) == Maybe.Some(null))
                    println(Maybe.Some(1) == Maybe.Some(null))
                    println(Maybe.Some(null) == Maybe.Some(1))
                """)).isEqualTo("true\nfalse\nfalse\n");
    }

    @Test
    public void enumPayloadComparisonStopsAtTheFirstDifference() {
        assertThat(run("""
                    class Noisy {
                        override func equals(other: Any?): Boolean {
                            println("noisy called")
                            return true
                        }
                        override func hashCode(): Integer {
                            return 1
                        }
                    }

                    enum Pair {
                        P(Integer, Noisy)
                    }

                    val n = Noisy()
                    println(Pair.P(1, n) == Pair.P(2, n))
                """)).isEqualTo("false\n");
    }

    @Test
    public void regexMatchSnapshotsCompareByStartEndAndGroups() {
        assertThat(run("""
                    val optional = Regex("(a)(b)?")
                    val a0 = optional.find("a")
                    val a1 = optional.find("a")
                    val ab = optional.find("ab")
                    println(a0 == a1)
                    println(a0 == ab)
                    println(a0 != ab)
                    val atStart = Regex("a").find("a")
                    val later = Regex("a").find("ba")
                    println(atStart == later)
                    println(Regex("a+").find("a") == Regex("a+").find("aa"))
                    println(Regex("a").find("a") == Regex("(a)").find("a"))
                """)).isEqualTo("true\nfalse\ntrue\nfalse\nfalse\nfalse\n");
    }

    @Test
    public void collectionsUseTheUserOverrideForAddRemoveAndPut() {
        assertThat(run("""
                    class Point {
                        val x: Integer

                        Point(x: Integer) {
                            this.x = x
                        }

                        override func equals(other: Any?): Boolean {
                            if (other is Point) {
                                return this.x == other.x
                            }
                            return false
                        }
                        override func hashCode(): Integer {
                            return this.x
                        }
                    }

                    var s: Set<Point> = Set()
                    println(s.add(Point(1)))
                    println(s.add(Point(1)))
                    println(s.size)
                    println(s.remove(Point(1)))
                    println(s.size)

                    var m: Map<Point, String> = Map()
                    m.put(Point(1), "one")
                    m.put(Point(1), "uno")
                    println(m.size)
                    println(m.get(Point(1)))
                    println(m.remove(Point(1)))
                    println(m.size)
                """)).isEqualTo("true\nfalse\n1\ntrue\n0\n1\nuno\ntrue\n0\n");
    }

    @Test
    public void nanCollectionKeysFollowSemanticEquality() {
        assertThat(run("""
                    var m: Map<Double, Integer> = Map()
                    m.put(0.0 / 0.0, 1)
                    m.put(0.0 / 0.0, 2)
                    println(m.size)
                    println(m.containsKey(0.0 / 0.0))
                """)).isEqualTo("2\nfalse\n");
    }

    @Test
    public void equalsDispatchesVirtuallyThroughBroadStaticTypes() {
        assertThat(run("""
                    interface Tag {
                        func tag(): Integer
                    }

                    class Item implements Tag {
                        val id: Integer

                        Item(id: Integer) {
                            this.id = id
                        }

                        func tag(): Integer {
                            return this.id
                        }

                        override func equals(other: Any?): Boolean {
                            if (other is Item) {
                                return this.id == other.id
                            }
                            return false
                        }
                        override func hashCode(): Integer {
                            return this.id
                        }
                    }

                    val viaInterface: Tag = Item(1)
                    val viaAny: Any = Item(1)
                    println(viaInterface == Item(1))
                    println(viaAny == Item(1))
                """)).isEqualTo("true\ntrue\n");
    }

    @Test
    public void notEqualsInvokesTheOverrideExactlyOnceAndNegates() {
        assertThat(run("""
                    class Counter {
                        var calls: Integer

                        Counter() {
                            this.calls = 0
                        }
                    }

                    class Item {
                        val counter: Counter

                        Item(counter: Counter) {
                            this.counter = counter
                        }

                        override func equals(other: Any?): Boolean {
                            this.counter.calls = this.counter.calls + 1
                            return false
                        }
                        override func hashCode(): Integer {
                            return 1
                        }
                    }

                    val counter = Counter()
                    val a = Item(counter)
                    val b = Item(counter)
                    println(a != b)
                    println(counter.calls)
                """)).isEqualTo("true\n1\n");
    }

    @Test
    public void arithmeticFailureFromEqualsPropagates() {
        Throwable thrown = catchThrowable(() -> run("""
                    class Bad {
                        override func equals(other: Any?): Boolean {
                            return 1 / 0 == 0
                        }
                        override func hashCode(): Integer {
                            return 1
                        }
                    }

                    val a = Bad()
                    val b = Bad()
                    println(a == b)
                """));
        assertThat(thrown).isNotNull();
    }

    @Test
    public void anOpenEqualsOverrideCanBeOverriddenAgain() {
        assertThat(run("""
                    open class Base {
                        open override func equals(other: Any?): Boolean {
                            return true
                        }
                        open override func hashCode(): Integer {
                            return 1
                        }
                    }

                    class Derived extends Base {
                        override func equals(other: Any?): Boolean {
                            return false
                        }
                        override func hashCode(): Integer {
                            return 1
                        }
                    }

                    println(Base() == Base())
                    println(Derived() == Derived())
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void regexMatchPropertiesExposeTheSnapshot() {
        assertThat(run("""
                    val m = Regex("(a)(b)?").find("ab")
                    if (m != null) {
                        println(m.value)
                        println(m.start)
                        println(m.end)
                        println(m.groupCount)
                        println(m.group(1))
                        println(m.group(2))
                    }
                """)).isEqualTo("ab\n0\n2\n2\na\nb\n");
    }

    @Test
    public void anyOperandsOfDifferentScalarKindsAreNeverEqual() {
        assertThat(run("""
                    func cmp(a: Any, b: Any): Boolean {
                        return a == b
                    }

                    func nothing(): Unit {
                    }

                    val s = "sentinel"
                    println(cmp(1, s))
                    println(cmp(1L, s))
                    println(cmp(Byte(1), s))
                    println(cmp(Short(1), s))
                    println(cmp(1.5f, s))
                    println(cmp(1.5, s))
                    println(cmp(true, s))
                    println(cmp('a', s))
                    println(cmp(s, 1))
                    println(cmp(s, 'a'))
                    println(cmp(nothing(), s))
                    println(cmp(s, nothing()))
                """)).isEqualTo("false\nfalse\nfalse\nfalse\nfalse\nfalse\nfalse\nfalse\nfalse\nfalse\nfalse\nfalse\n");
    }

    @Test
    public void anyOperandsOfDifferentBuiltinKindsAreNeverEqual() {
        assertThat(run("""
                    func cmp(a: Any, b: Any): Boolean {
                        return a == b
                    }

                    println(cmp(Regex("a"), "x"))
                    println(cmp(Regex("a"), 1))
                    println(cmp(Regex("a"), List<Integer>(1)))

                    val m = Regex("a").find("a")
                    if (m != null) {
                        println(cmp(m, "x"))
                        println(cmp(m, Regex("a")))
                    }
                """)).isEqualTo("false\nfalse\nfalse\nfalse\nfalse\n");
    }

    @Test
    public void regexMatchIntegerPropertiesAreUsableAsIntegers() {
        assertThat(run("""
                    val m = Regex("(a)(b)?").find("ab")
                    if (m != null) {
                        val span: Integer = m.end - m.start
                        val groups: Integer = m.groupCount + 1
                        println(span)
                        println(groups)
                    }
                """)).isEqualTo("2\n3\n");
    }

    @Test
    public void differentEnumTypesComparedThroughAnyAreUnequal() {
        assertThat(run("""
                    enum Color {
                        Red
                    }

                    enum Size {
                        Red
                    }

                    func cmp(a: Any, b: Any): Boolean {
                        return a == b
                    }

                    println(cmp(Color.Red, Size.Red))
                    println(cmp(Color.Red, Color.Red))
                    println(cmp(Size.Red, Size.Red))
                """)).isEqualTo("false\ntrue\ntrue\n");
    }

    @Test
    public void nanEnumPayloadFollowsIeeeEquality() {
        assertThat(run("""
                    enum Box {
                        N(Double)
                    }

                    println(Box.N(0.0 / 0.0) == Box.N(0.0 / 0.0))
                    println(Box.N(1.0) == Box.N(1.0))
                """)).isEqualTo("false\ntrue\n");
    }

    @Test
    public void enumKeysWithUserPayloadsUseTheOverride() {
        assertThat(run("""
                    class Point {
                        val x: Integer

                        Point(x: Integer) {
                            this.x = x
                        }

                        override func equals(other: Any?): Boolean {
                            if (other is Point) {
                                return this.x == other.x
                            }
                            return false
                        }
                        override func hashCode(): Integer {
                            return this.x
                        }
                    }

                    enum Key {
                        K(Point)
                    }

                    var m: Map<Key, String> = Map()
                    m.put(Key.K(Point(1)), "one")
                    println(m.get(Key.K(Point(1))))
                    println(m.containsKey(Key.K(Point(2))))
                """)).isEqualTo("one\nfalse\n");
    }

    @Test
    public void repeatedRegexEvaluationDoesNotChangeEquality() {
        assertThat(run("""
                    var i = 0
                    while (i < 3) {
                        val constant = Regex("a+")
                        val dynamic = Regex("a" .. "+")
                        println(constant == dynamic)
                        i = i + 1
                    }
                """)).isEqualTo("true\ntrue\ntrue\n");
    }

    @Test
    public void notEqualsRendersBooleanForEveryScalarKind() {
        assertThat(run("""
                    println('A' != 'B')
                    println("x" != "y")
                    println(1 != 2)
                    println(1L != 2L)
                    println(1.5f != 2.5f)
                    println(1.5 != 2.5)
                    println(true != false)
                    println(Byte(1) != Byte(2))
                    println(Short(1) != Short(2))
                """)).isEqualTo("true\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\n");
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
            return Source.newBuilder("solvik", source, "equality.sol").build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
