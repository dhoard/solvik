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
 * Exercises `hashCode()` on every value kind a Solvik program can construct, through the real
 * compiler and AST rather than by calling the hash service directly.
 *
 * <p>{@code SolvikHashInvariantTest} calls {@code SolvikHash} and {@code SolvikValues} in process.
 * That proves the two services agree with each other, but it leaves the dispatch nodes unexercised
 * and depends on the test choosing the right runtime objects. This test goes through parsing, name
 * resolution, static type analysis, lowering, and execution, so it covers what a direct call cannot:
 * that a `hashCode()` send lowers to the right node for each receiver kind, and that no constructible
 * value reaches a terminal {@code AssertionError} in either shared service.
 *
 * <p>The terminal throws in {@code SolvikHash} and {@code SolvikValues} are unreachable by
 * construction: each dispatch is exhaustive over the kinds its own guard admits, and
 * {@code SolvikHashTotalityTest} pins that no foreign value can enter. That is a claim about the
 * language surface, so it is tested on the surface, by touching every kind the surface exposes and
 * requiring the program to run to completion.
 *
 * <p>The golden is derived from the executed program and reviewed line by line against the equality
 * rules in docs/LANGUAGE_SPEC.md section 3, not transcribed from expectation.
 */
public class SolvikHashKindCoverageTest {

    @Test
    public void everyConstructibleValueKindHasAHashAndBehavesAsAKey() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("solvik").out(out).err(out).allowAllAccess(true).build()) {
            try {
                context.eval(Source.newBuilder("solvik", PROGRAM, "kinds.sol").build());
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo(EXPECTED);
    }

    private static final String PROGRAM = """
            class Scalar {
                val id: Integer

                Scalar(id: Integer) {
                    this.id = id
                }

                override func equals(other: Any?): Boolean {
                    if (other is Scalar) {
                        return this.id == other.id
                    }
                    return false
                }

                override func hashCode(): Integer {
                    return this.id
                }
            }

            enum Payload {
                One
                Two(Integer)
                Three(Integer, String)
                Four(Scalar)
            }

            var boolSeen: Set<Boolean> = Set();
            println(true.hashCode() == false.hashCode());
            boolSeen.add(true);
            boolSeen.add(true);
            boolSeen.add(false);
            println(boolSeen.size);

            var charSeen: Set<Character> = Set();
            println('a'.hashCode() == 'a'.hashCode());
            charSeen.add('a');
            charSeen.add('b');
            println(charSeen.size);

            var intSeen: Set<Integer> = Set();
            println((7).hashCode() == (7).hashCode());
            intSeen.add(7);
            intSeen.add(7);
            intSeen.add(8);
            println(intSeen.size);

            var longSeen: Set<Long> = Set();
            println(9L.hashCode() == 9L.hashCode());
            longSeen.add(9L);
            longSeen.add(9L);
            longSeen.add(10L);
            println(longSeen.size);

            var byteSeen: Set<Byte> = Set();
            println(Byte(3).hashCode() == Byte(3).hashCode());
            byteSeen.add(Byte(3));
            byteSeen.add(Byte(3));
            println(byteSeen.size);

            var shortSeen: Set<Short> = Set();
            println(Short(4).hashCode() == Short(4).hashCode());
            shortSeen.add(Short(4));
            shortSeen.add(Short(4));
            println(shortSeen.size);

            var doubleSeen: Set<Double> = Set();
            println(1.5.hashCode() == 1.5.hashCode());
            doubleSeen.add(1.5);
            doubleSeen.add(1.5);
            doubleSeen.add(2.5);
            println(doubleSeen.size);

            var zeroSeen: Set<Double> = Set();
            println(0.0.hashCode() == -0.0.hashCode());
            zeroSeen.add(0.0);
            zeroSeen.add(-0.0);
            println(zeroSeen.size);

            var floatSeen: Set<Float> = Set();
            println(Float(1.5).hashCode() == Float(1.5).hashCode());
            floatSeen.add(Float(1.5));
            floatSeen.add(Float(1.5));
            println(floatSeen.size);

            var stringSeen: Set<String> = Set();
            println("ab".hashCode() == "ab".hashCode());
            stringSeen.add("ab");
            stringSeen.add("ab");
            stringSeen.add("cd");
            println(stringSeen.size);

            var unitSeen: Set<Unit> = Set();
            var unitValue: Unit = println("");
            unitSeen.add(unitValue);
            println(unitSeen.size);

            var enumSeen: Set<Payload> = Set();
            println(Payload.One.hashCode() == Payload.One.hashCode());
            enumSeen.add(Payload.One);
            enumSeen.add(Payload.One);
            enumSeen.add(Payload.Two(1));
            enumSeen.add(Payload.Two(1));
            enumSeen.add(Payload.Three(1, "x"));
            println(enumSeen.size);

            var payloadSeen: Set<Payload> = Set();
            payloadSeen.add(Payload.Four(Scalar(3)));
            payloadSeen.add(Payload.Four(Scalar(3)));
            println(payloadSeen.size);
            println(Payload.Four(Scalar(3)).hashCode() == Payload.Four(Scalar(3)).hashCode());

            var regexSeen: Set<Regex> = Set();
            var pattern: Regex = Regex("a+");
            regexSeen.add(pattern);
            regexSeen.add(Regex("a+"));
            println(regexSeen.size);

            var maybeFound: RegexMatch? = pattern.find("aaa");
            if (maybeFound != null) {
                var found: RegexMatch = maybeFound;
                var maybeAgain: RegexMatch? = pattern.find("aaa");
                var matchSeen: Set<RegexMatch> = Set(found);
                if (maybeAgain != null) {
                    matchSeen.add(maybeAgain);
                }
                println(matchSeen.size);
                println(found.start.hashCode() == found.start.hashCode());
                println(found.value.hashCode() == "aaa".hashCode());
            }

            var listOne: List<Integer> = List(1, 2);
            var listTwo: List<Integer> = List(1, 2);
            var listSeen: Set<List<Integer>> = Set(listOne);
            listSeen.add(listOne);
            listSeen.add(listTwo);
            println(listSeen.size);

            var innerList: List<Integer> = List(1);
            var innerSet: Set<Integer> = Set(1);
            var innerMap: Map<Integer, Integer> = Map();
            var innerStack: Stack<Integer> = Stack();
            var mixedSeen: Set<Any> = Set(innerSet, innerMap, innerStack);
            println(mixedSeen.size);

            var scalarSeen: Set<Scalar> = Set();
            scalarSeen.add(Scalar(1));
            scalarSeen.add(Scalar(1));
            println(scalarSeen.size);

            var nothing: Any? = null;
            println(nothing?.hashCode() == null);
            var nullSeen: Set<Any?> = Set();
            nullSeen.add(null);
            nullSeen.add(null);
            println(nullSeen.size);

            var keys: Map<Any?, Integer> = Map();
            keys.put(1, 1);
            keys.put("one", 2);
            keys.put(Scalar(5), 3);
            keys.put(Payload.Two(2), 4);
            keys.put(null, 5);
            println(keys.size);
            println(keys.get(Scalar(5)));
            println(keys.get(null));

            var deepKey: List<Integer> = List(1, 2);
            var deep: Map<List<Integer>, Integer> = Map(deepKey: 6);
            // Collections key by reference identity, so only the same instance finds the entry.
            println(deep.get(deepKey));
            println(deep.size);
        """;

    /**
     * One line per `println`, in order. Each is the number of distinct values a `Set` kept, or a hash
     * agreement check. The blank line after the `true` for `String` is produced by `println("")` when
     * building the `Unit` value, and `unitSeen` then keeps one entry because all `Unit` are equal. Two
     * `List`s with equal content stay two entries because collections key by reference identity, and
     * `0.0` with `-0.0` collapses to one because Solvik floating equality is IEEE `==`.
     */
    private static final String EXPECTED_LINES = """
            false
            2
            true
            2
            true
            2
            true
            2
            true
            1
            true
            1
            true
            2
            true
            1
            true
            1
            true
            2

            1
            true
            3
            1
            true
            1
            1
            true
            true
            2
            3
            1
            true
            1
            5
            3
            5
            6
            1

            """;

    /**
     * The exact guest stdout: the literal line list above with one trailing newline. Computed rather
     * than spelled out so text block incidental indentation and trailing-blank-line handling cannot
     * silently change what the program is compared against.
     */
    private static final String EXPECTED = EXPECTED_LINES.stripTrailing() + "\n";
}
