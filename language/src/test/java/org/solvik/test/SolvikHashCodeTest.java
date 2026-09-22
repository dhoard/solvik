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
import static org.solvik.test.SolvikTestSupport.parseOk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.solvik.ast.CompilationUnitNode;
import org.solvik.diagnostic.Diagnostic;
import org.solvik.diagnostic.DiagnosticBag;
import org.solvik.diagnostic.DiagnosticCode;
import org.solvik.diagnostic.DiagnosticSeverity;
import org.solvik.semantic.SemanticResult;
import org.solvik.semantic.SolvikSemanticAnalyzer;

/**
 * The universal {@code Any.hashCode()} member and its pairing with {@code equals}
 * (docs/LANGUAGE_SPEC.md section 3).
 *
 * <p>The behavioral core is the invariant every hash container depends on: values that are
 * {@code ==} always hash alike. Tests cover the fixed rules for every built-in kind, user dispatch
 * and the identity default, the special {@code 0.0}/{@code -0.0} and {@code NaN} leaves where Solvik
 * IEEE equality diverges from boxed Java equality, {@code super.hashCode()} in both its inherited and
 * root-default forms, and the two static rules that make a class override the two members together.
 */
public final class SolvikHashCodeTest {

    private static String run(String program) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            context.eval(build(Source.newBuilder("solvik", program, "hashcode.sol")));
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Source build(Source.Builder builder) {
        try {
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Analyzes a program that is expected to carry at least one error, and returns its diagnostics. */
    private static DiagnosticBag checkFails(String source) {
        CompilationUnitNode unit = parseOk("hashcode.sol", source);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("analysis must fail: " + source).isFalse();
        assertThat(result.diagnostics().hasErrors()).as("failed analysis must carry diagnostics").isTrue();
        return result.diagnostics();
    }

    /** Every error the analysis of a program reports, in emission order. */
    private static List<DiagnosticCode> errorCodes(String source) {
        CompilationUnitNode unit = parseOk("hashcode.sol", source);
        return SolvikSemanticAnalyzer.analyze(unit).diagnostics().all().stream() //
                        .filter(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR) //
                        .map(Diagnostic::code).toList();
    }

    private static DiagnosticCode firstCode(String source) {
        return errorCodes(source).get(0);
    }

    // ---------------------------------------------------------------- built-in fixed rules

    @Test
    public void integralScalarsHashByValue() {
        assertThat(run("""
                    var a: Integer = 7;
                    var b: Integer = 7;
                    println(a.hashCode() == b.hashCode());
                    println(a.hashCode());
                    var big: Long = 5000000000L;
                    println(big.hashCode() == 5000000000L.hashCode());
                    println((1 + 2).hashCode() == 3.hashCode());
                """)).isEqualTo("true\n7\ntrue\ntrue\n");
    }

    @Test
    public void stringsHashByContentAndDiffer() {
        assertThat(run("""
                    println("abc".hashCode() == "abc".hashCode());
                    println("abc".hashCode() == "abd".hashCode());
                    println("".hashCode() == "".hashCode());
                """)).isEqualTo("true\nfalse\ntrue\n");
    }

    @Test
    public void booleanAndCharacterScalarsHashConsistently() {
        assertThat(run("""
                    println(true.hashCode() == true.hashCode());
                    println(true.hashCode() == false.hashCode());
                    println('x'.hashCode() == 'x'.hashCode());
                    println('x'.hashCode() == 'y'.hashCode());
                """)).isEqualTo("true\nfalse\ntrue\nfalse\n");
    }

    @Test
    public void zeroAndNegativeZeroAreEqualAndHashAlike() {
        // Solvik uses IEEE `==`, where 0.0 equals -0.0, while Java's Double.hashCode separates them.
        // The hash canonicalizes negative zero so the invariant holds, and a Set keeps one element.
        assertThat(run("""
                    var z: Double = 0.0;
                    var mz: Double = -0.0;
                    println(z == mz);
                    println(z.hashCode() == mz.hashCode());
                    var s: Set<Double> = Set<Double>();
                    println(s.add(0.0));
                    println(s.add(-0.0));
                    println(s.size);
                """)).isEqualTo("true\ntrue\ntrue\nfalse\n1\n");
    }

    @Test
    public void nanIsUnequalButHashesStably() {
        // NaN is unequal even to itself. Hashing equal values alike is required; hashing unequal
        // values alike is permitted, so NaN keeps a stable hash without violating the invariant.
        assertThat(run("""
                    var n: Double = 0.0 / 0.0;
                    println(n == n);
                    println(n.hashCode() == n.hashCode());
                    var f: Float = 0.0f / 0.0f;
                    println(f == f);
                    println(f.hashCode() == f.hashCode());
                """)).isEqualTo("false\ntrue\nfalse\ntrue\n");
    }

    @Test
    public void floatsMatchTheirIntegralValue() {
        assertThat(run("""
                    var a: Float = 1.5f;
                    println(a.hashCode() == 1.5f.hashCode());
                    println(a.hashCode() == 2.5f.hashCode());
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void nullableReceiversUseTheSafeCallForm() {
        // A nullable receiver must use `?.`, matching every other universal member: a null receiver
        // yields null, and a present one dispatches the ordinary fixed rule.
        assertThat(run("""
                    var a: Integer? = null;
                    println(a?.hashCode() == null);
                    var b: Integer? = 7;
                    println(b?.hashCode() == 7.hashCode());
                    println(a?.hashCode());
                """)).isEqualTo("true\ntrue\nnull\n");
    }

    @Test
    public void unitHashesConsistently() {
        assertThat(run("""
                    var u: Unit = println("a");
                    var v: Unit = println("b");
                    println(u.hashCode() == v.hashCode());
                """)).isEqualTo("a\nb\ntrue\n");
    }

    // ---------------------------------------------------------------- user dispatch

    @Test
    public void userOverrideIsUsedForEqualObjectsAndThroughAnyReceivers() {
        assertThat(run("""
                    open class Point {
                        val x: Integer
                        val y: Integer
                        Point(x: Integer, y: Integer) {
                            this.x = x
                            this.y = y
                        }
                        open override func equals(other: Any?): Boolean {
                            if (other is Point) {
                                return this.x == other.x && this.y == other.y;
                            }
                            return false;
                        }
                        open override func hashCode(): Integer {
                            return 31 * this.x + this.y;
                        }
                    }
                    var a: Point = Point(1, 2);
                    var b: Point = Point(1, 2);
                    var c: Point = Point(9, 9);
                    println(a == b);
                    println(a.hashCode() == b.hashCode());
                    println(a == c);
                    var o: Any = a;
                    var p: Any = b;
                    println(o.hashCode() == p.hashCode());
                """)).isEqualTo("true\ntrue\nfalse\ntrue\n");
    }

    @Test
    public void equalUserObjectsCollapseInASetAndMap() {
        // The invariant, exercised through the containers that need it.
        assertThat(run("""
                    class Point {
                        val x: Integer
                        Point(x: Integer) {
                            this.x = x
                        }
                        override func equals(other: Any?): Boolean {
                            if (other is Point) {
                                return this.x == other.x;
                            }
                            return false;
                        }
                        override func hashCode(): Integer {
                            return this.x;
                        }
                    }
                    var s: Set<Point> = Set(Point(1), Point(1), Point(2));
                    println(s.size);
                    println(s.contains(Point(1)));
                    println(s.contains(Point(3)));
                """)).isEqualTo("2\ntrue\nfalse\n");
    }

    @Test
    public void defaultEqualsAndHashBothTrackIdentity() {
        // With no overrides, equality is reference identity and so is the hash: same object agrees,
        // distinct objects disagree, and the two decisions never diverge.
        assertThat(run("""
                    class Plain {
                    }
                    var a: Plain = Plain();
                    var b: Plain = Plain();
                    println(a == b);
                    println(a.hashCode() == b.hashCode());
                    println(a == a);
                    println(a.hashCode() == a.hashCode());
                """)).isEqualTo("false\nfalse\ntrue\ntrue\n");
    }

    @Test
    public void hashCodeOverrideIsReachedFromAnAnyReceiver() {
        assertThat(run("""
                    class Wrapped {
                        val v: Integer
                        Wrapped(v: Integer) {
                            this.v = v
                        }
                        override func equals(other: Any?): Boolean {
                            if (other is Wrapped) {
                                return this.v == other.v;
                            }
                            return false;
                        }
                        override func hashCode(): Integer {
                            return this.v * 7;
                        }
                    }
                    var typed: Wrapped = Wrapped(6);
                    var erased: Any = Wrapped(6);
                    println(typed.hashCode());
                    println(erased.hashCode());
                """)).isEqualTo("42\n42\n");
    }

    @Test
    public void inheritedOverrideAppliesToASubclassWithoutItsOwn() {
        // Inheritance satisfies the pairing: the subclass inherits both members from its superclass.
        assertThat(run("""
                    open class Base {
                        open override func equals(other: Any?): Boolean {
                            return true;
                        }
                        open override func hashCode(): Integer {
                            return 5;
                        }
                    }
                    class Derived extends Base {
                    }
                    var a: Derived = Derived();
                    var b: Derived = Derived();
                    println(a == b);
                    println(a.hashCode() == b.hashCode());
                    println(a.hashCode());
                """)).isEqualTo("true\ntrue\n5\n");
    }

    @Test
    public void theOverrideIsReachedFromEveryStaticReceiverKind() {
        // `hashCode` is declared on `Any`, so it must resolve through any static type a value can
        // appear behind and still dispatch dynamically. Interface, numeric, concrete, and generic
        // receivers each take a different path through member resolution, and a fixed hash on a
        // widened numeric receiver is the case most likely to be silently mishandled.
        assertThat(run("""
                    interface Shape {
                        func name(): String
                    }

                    class Square implements Shape {
                        val side: Integer

                        Square(side: Integer) {
                            this.side = side
                        }

                        func name(): String {
                            return "square"
                        }

                        override func equals(other: Any?): Boolean {
                            if (other is Square) {
                                return this.side == other.side
                            }
                            return false
                        }

                        override func hashCode(): Integer {
                            return this.side
                        }
                    }

                    func hashOf<T>(item: T): Integer {
                        return item.hashCode();
                    }

                    var iface: Shape = Square(3);
                    println(iface.hashCode() == Square(3).hashCode());
                    var concrete: Square = Square(4);
                    println(concrete.hashCode() == Square(4).hashCode());
                    var num: Number = 5;
                    println(num.hashCode() == 5.hashCode());
                    println(hashOf(Square(5)) == Square(5).hashCode());
                    println(hashOf(7) == 7.hashCode());
                    println(hashOf("ab") == "ab".hashCode());
                """)).isEqualTo("true\ntrue\ntrue\ntrue\ntrue\ntrue\n");
    }

    @Test
    public void aSubclassAddingFieldsInheritsBothMembersUnchanged() {
        // Section 3 resolves this by construction: equality and hash both dispatch the *effective*
        // override, and the pairing rule is checked per declaration precisely because inheritance is
        // allowed to satisfy it. A subclass that adds a field and overrides neither member therefore
        // keeps comparing on whatever its superclass compared, consistently for both members. That is
        // the specified consequence of inherited dispatch, not an oversight: it is uniform, and the
        // invariant still holds because the same inherited rule answers both questions. A programmer who
        // wants the new field to matter overrides both members in the subclass.
        assertThat(run("""
                    open class Base {
                        val x: Integer

                        Base(x: Integer) {
                            this.x = x
                        }

                        override func equals(other: Any?): Boolean {
                            if (other is Base) {
                                return this.x == other.x
                            }
                            return false
                        }

                        override func hashCode(): Integer {
                            return this.x
                        }
                    }

                    class Derived extends Base {
                        val extra: Integer

                        Derived(x: Integer, extra: Integer) {
                            super(x)
                            this.extra = extra
                        }
                    }

                    var a: Derived = Derived(1, 100);
                    var b: Derived = Derived(1, 200);
                    println(a == b);
                    println(a.hashCode() == b.hashCode());
                    println(a == Derived(2, 100));
                """)).isEqualTo("true\ntrue\nfalse\n");
    }

    // ---------------------------------------------------------------- super.hashCode()

    @Test
    public void superHashCodeDispatchesToTheInheritedOverride() {
        assertThat(run("""
                    open class Base {
                        open override func equals(other: Any?): Boolean {
                            return true;
                        }
                        open override func hashCode(): Integer {
                            return 1;
                        }
                    }
                    class Derived extends Base {
                        override func equals(other: Any?): Boolean {
                            return false;
                        }
                        override func hashCode(): Integer {
                            return 2;
                        }
                        func hashViaSuper(): Integer {
                            return super.hashCode();
                        }
                    }
                    var d: Derived = Derived();
                    println(d.hashCode());
                    println(d.hashViaSuper());
                """)).isEqualTo("2\n1\n");
    }

    @Test
    public void superHashCodeReachesTheRootIdentityDefault() {
        // No class in the hierarchy overrides the member, so super.hashCode() is the identity default
        // and must not re-dispatch to the current class's own override (which would recurse).
        assertThat(run("""
                    open class Base {
                    }
                    class Derived extends Base {
                        override func equals(other: Any?): Boolean {
                            return super.equals(other);
                        }
                        override func hashCode(): Integer {
                            return super.hashCode();
                        }
                    }
                    var a: Derived = Derived();
                    var b: Derived = Derived();
                    println(a == a);
                    println(a == b);
                    println(a.hashCode() == a.hashCode());
                    println(a.hashCode() == b.hashCode());
                """)).isEqualTo("true\nfalse\ntrue\nfalse\n");
    }

    @Test
    public void derivedPairStaysConsistentWhenAddingFieldsToBoth() {
        // A subclass that adds an equality-relevant field mixes the superclass hash with it, so the
        // two decisions remain aligned across the hierarchy.
        assertThat(run("""
                    open class Base {
                        val id: Integer
                        Base(id: Integer) {
                            this.id = id
                        }
                        open override func equals(other: Any?): Boolean {
                            if (other is Base) {
                                return this.id == other.id;
                            }
                            return false;
                        }
                        open override func hashCode(): Integer {
                            return this.id;
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
                                return super.equals(other) && this.extra == other.extra;
                            }
                            return false;
                        }
                        override func hashCode(): Integer {
                            return 31 * super.hashCode() + this.extra;
                        }
                    }
                    println(Derived(1, 2) == Derived(1, 2));
                    println(Derived(1, 2).hashCode() == Derived(1, 2).hashCode());
                    println(Derived(1, 2) == Derived(1, 3));
                """)).isEqualTo("true\ntrue\nfalse\n");
    }

    // ---------------------------------------------------------------- enums and built-ins

    @Test
    public void enumValuesHashConsistentlyWithTheirValueEquality() {
        assertThat(run("""
                    enum Color {
                        Red
                        Green
                    }
                    println(Color.Red.hashCode() == Color.Red.hashCode());
                    println(Color.Red.hashCode() == Color.Green.hashCode());
                """)).isEqualTo("true\nfalse\n");
    }

    @Test
    public void regexValuesHashBySourceText() {
        assertThat(run("""
                    var a: Regex = Regex("a+");
                    var b: Regex = Regex("a+");
                    println(a == b);
                    println(a.hashCode() == b.hashCode());
                    println(a.hashCode() == Regex("b+").hashCode());
                """)).isEqualTo("true\ntrue\nfalse\n");
    }

    @Test
    public void collectionsHashByReferenceIdentity() {
        // Collection equality is reference identity, so the hash must be the identity hash.
        assertThat(run("""
                    var a: List<Integer> = List(1, 2);
                    var b: List<Integer> = List(1, 2);
                    println(a == b);
                    println(a.hashCode() == b.hashCode());
                    println(a.hashCode() == a.hashCode());
                """)).isEqualTo("false\nfalse\ntrue\n");
    }

    // ---------------------------------------------------------------- static pairing rules

    @Test
    public void aDirectHashCodeCallOnANullableReceiverIsRejected() {
        // The only send to `hashCode` that observes a null receiver is the safe call, which returns
        // null. A direct send on a possibly-null receiver is a static error, so the non-safe null
        // branch of the hash node is unreachable by rule rather than by accident. This pins that rule:
        // if the rejection were ever relaxed, the untested branch would become reachable and this test
        // would fail before the behavior could silently diverge.
        assertThat(errorCodes("""
                    var maybe: Integer? = null;
                    println(maybe.hashCode());
                """)).containsExactly(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE);
        assertThat(errorCodes("""
                    class Key {
                        override func equals(other: Any?): Boolean {
                            return false
                        }

                        override func hashCode(): Integer {
                            return 1
                        }
                    }

                    var maybe: Key? = null;
                    println(maybe.hashCode());
                """)).containsExactly(DiagnosticCode.TYPE_NULLABLE_DEREFERENCE);
    }

    @Test
    public void equalsWithoutHashCodeIsRejected() {
        String text = """
                class Point {
                    val x: Integer
                    Point(x: Integer) {
                        this.x = x
                    }
                    override func equals(other: Any?): Boolean {
                        return other is Point
                    }
                }
                """;
        assertThat(firstCode(text)).isEqualTo(DiagnosticCode.SEM_EQUALS_WITHOUT_HASHCODE);
    }

    @Test
    public void hashCodeWithoutEqualsIsRejected() {
        String text = """
                class Point {
                    val x: Integer
                    Point(x: Integer) {
                        this.x = x
                    }
                    override func hashCode(): Integer {
                        return this.x
                    }
                }
                """;
        assertThat(firstCode(text)).isEqualTo(DiagnosticCode.SEM_HASHCODE_WITHOUT_EQUALS);
    }

    @Test
    public void theTwoPairingErrorsAreIndependent() {
        // A class missing hashCode reports only the equals diagnostic, never the reverse one, so the
        // message names the member that is actually unpaired.
        assertThat(errorCodes("""
                class A {
                    override func equals(other: Any?): Boolean {
                        return true
                    }
                }
                """)).containsExactly(DiagnosticCode.SEM_EQUALS_WITHOUT_HASHCODE);
        assertThat(errorCodes("""
                class A {
                    override func hashCode(): Integer {
                        return 1
                    }
                }
                """)).containsExactly(DiagnosticCode.SEM_HASHCODE_WITHOUT_EQUALS);
    }

    @Test
    public void aNonOverrideNamedHashCodeNeedsTheOverrideModifier() {
        // A bare hashCode is reported as an accidental override and does not satisfy the pairing, so
        // the class that pairs it with an equals override still reports the missing hashCode.
        String text = """
                class A {
                    func hashCode(): Integer {
                        return 1
                    }
                    override func equals(other: Any?): Boolean {
                        return true
                    }
                }
                """;
        assertThat(errorCodes(text)).contains(DiagnosticCode.SEM_ACCIDENTAL_OVERRIDE, DiagnosticCode.SEM_EQUALS_WITHOUT_HASHCODE);
    }

    @Test
    public void hashCodeSignatureMustMatchTheRootMember() {
        assertThat(firstCode("""
                class A {
                    override func hashCode(): Long {
                        return 1
                    }
                    override func equals(other: Any?): Boolean {
                        return true
                    }
                }
                """)).isEqualTo(DiagnosticCode.SEM_OVERRIDE_SIGNATURE);
        String withArgument = """
                class A {
                    override func equals(other: Any?): Boolean {
                        return true
                    }
                    override func hashCode(seed: Integer): Integer {
                        return seed
                    }
                }
                """;
        assertThat(errorCodes(withArgument)).contains(DiagnosticCode.SEM_OVERRIDE_SIGNATURE);
    }

    @Test
    public void hashCodeIsReservedForInterfacesAndProperties() {
        assertThat(firstCode("""
                interface I {
                    func hashCode(): Integer
                }
                """)).isEqualTo(DiagnosticCode.SEM_RESERVED_MEMBER);
        assertThat(firstCode("""
                class A {
                    val hashCode: Integer
                    A() {
                        this.hashCode = 1
                    }
                }
                """)).isEqualTo(DiagnosticCode.SEM_RESERVED_MEMBER);
    }

    @Test
    public void aWellPairedOverrideCompilesCleanly() {
        String text = """
                class Point {
                    val x: Integer
                    Point(x: Integer) {
                        this.x = x
                    }
                    override func equals(other: Any?): Boolean {
                        return other is Point
                    }
                    override func hashCode(): Integer {
                        return 1
                    }
                }
                """;
        CompilationUnitNode unit = parseOk("hashcode.sol", text);
        SemanticResult result = SolvikSemanticAnalyzer.analyze(unit);
        assertThat(result.isSuccess()).as("a paired override must compile: " + result.diagnostics().all()).isTrue();
    }

    @Test
    public void callingHashCodeWithAnArgumentIsRejected() {
        String text = """
                class Point {
                    override func equals(other: Any?): Boolean {
                        return true
                    }
                    override func hashCode(): Integer {
                        return 1
                    }
                }
                val p = Point()
                val h = p.hashCode(1)
                """;
        assertThat(errorCodes(text)).contains(DiagnosticCode.TYPE_ARITY_MISMATCH);
    }

    @Test
    public void aRuntimeFailureInAnOverridePropagates() {
        try (Context context = Context.newBuilder("solvik").out(new ByteArrayOutputStream()).allowAllAccess(true).build()) {
            PolyglotException failure = null;
            try {
                context.eval(build(Source.newBuilder("solvik", """
                        class Bad {
                            override func equals(other: Any?): Boolean {
                                return false
                            }
                            override func hashCode(): Integer {
                                return 1 / 0
                            }
                        }
                        val a = Bad()
                        println(a.hashCode())
                        """, "hashcode.sol")));
            } catch (PolyglotException e) {
                failure = e;
            }
            assertThat(failure).isNotNull();
        }
    }

    @Test
    public void collectionsStayCorrectWhenEveryKeyCollidesInOneBucket() {
        // A constant hash is perfectly legal under the documented invariant: equal objects agree, and
        // unequal objects are merely allowed to collide. It is simply the slowest legal choice. So
        // membership must still be decided by `equals`, and a future hash index must degrade to full
        // scanning rather than wrong answers. Today `Set` and `Map` always scan by equality; this is the
        // guard that an index must keep that property even at its worst-case distribution.
        assertThat(run("""
                    class Violator {
                        val id: Integer

                        Violator(id: Integer) {
                            this.id = id
                        }

                        override func equals(other: Any?): Boolean {
                            if (other is Violator) {
                                return this.id == other.id
                            }
                            return false
                        }

                        override func hashCode(): Integer {
                            // Legal but pathological: every distinct id hashes alike.
                            return 1
                        }
                    }

                    var s: Set<Violator> = Set(Violator(1), Violator(1), Violator(2));
                    println(s.size);
                    println(s.contains(Violator(2)));
                    println(s.contains(Violator(3)));
                    var m: Map<Violator, String> = Map();
                    m.put(Violator(1), "one");
                    m.put(Violator(1), "uno");
                    println(m.size);
                    println(m.get(Violator(1)));
                """)).isEqualTo("2\ntrue\nfalse\n1\nuno\n");
    }

    @Test
    public void membershipSurvivesAnEqualsInconsistentHashCode() {
        // Stronger than the all-collide case above, and the case that decides how a future index must
        // be built. Here `equals` deliberately ignores a field that `hashCode` uses, so two semantically
        // equal keys can receive different hashes. That code is accepted (the compiler cannot reason
        // about a method body), so membership must still be decided by `equals`.
        //
        // This is not hypothetical: an index that buckets by hash and then confirms every surviving
        // candidate with the real equality scan -- narrowing only, never accepting on hash agreement --
        // was measured returning false here, because the equal element was skipped before the scan ever
        // ran. Confirming a hit does not protect against skipping a candidate. See docs/ARCHITECTURE.md.
        assertThat(run("""
                    class Inconsistent {
                        val id: Integer
                        var noise: Integer

                        Inconsistent(id: Integer, noise: Integer) {
                            this.id = id
                            this.noise = noise
                        }

                        override func equals(other: Any?): Boolean {
                            // Equality deliberately ignores `noise` ...
                            if (other is Inconsistent) {
                                return this.id == other.id
                            }
                            return false
                        }

                        override func hashCode(): Integer {
                            // ... while the hash is driven entirely by it.
                            return this.noise
                        }
                    }

                    var s: Set<Inconsistent> = Set();
                    s.add(Inconsistent(1, 3));
                    println(s.contains(Inconsistent(1, 4)));
                    println(s.contains(Inconsistent(1, 3)));
                    println(s.size);
                    var m: Map<Inconsistent, String> = Map();
                    m.put(Inconsistent(1, 3), "stored");
                    println(m.get(Inconsistent(1, 4)));
                    println(m.size);
                """)).isEqualTo("true\ntrue\n1\nstored\n1\n");
    }

    @Test
    public void scalarKeysStayCorrectThroughTheEqualityScan() {
        // Unequal keys must not collapse and an equal key must update in place rather than append.
        assertThat(run("""
                    var keys: Set<Integer> = Set(1, 2, 3, 1, 2);
                    println(keys.size);
                    println(keys.contains(3));
                    println(keys.contains(4));
                    var counts: Map<Integer, Integer> = Map();
                    counts.put(7, 1);
                    counts.put(3 + 4, 2);
                    println(counts.size);
                    println(counts.get(7));
                """)).isEqualTo("3\ntrue\nfalse\n1\n2\n");
    }
}
