package org.solvik.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.solvik.truffle.SolvikHash;
import org.solvik.truffle.SolvikValues;
import org.solvik.truffle.object.SolvikEnumClass;
import org.solvik.truffle.object.SolvikEnumValue;
import org.solvik.truffle.object.SolvikEnumVariant;
import org.solvik.truffle.object.SolvikList;
import org.solvik.truffle.object.SolvikMap;
import org.solvik.truffle.object.SolvikRegex;
import org.solvik.truffle.object.SolvikRegexMatch;
import org.solvik.truffle.object.SolvikSet;
import org.solvik.truffle.object.SolvikStack;
import org.solvik.truffle.SolvikUnit;
import org.solvik.truffle.object.SolvikAny;
import org.solvik.truffle.object.SolvikClass;
import org.solvik.type.EnumType;

/**
 * Exhaustive check of the hash/equality invariant over every Solvik runtime value kind
 * (docs/LANGUAGE_SPEC.md section 3): for every pair drawn from a population that covers all kinds,
 * {@code SolvikValues.equal(a, b)} implies {@code SolvikHash.hash(a).equals(SolvikHash.hash(b))}.
 *
 * <p>The existing {@code SolvikHashCodeTest} pins the invariant through guest programs, which proves
 * the pipeline wires the service correctly for the shapes those programs happen to write. This test
 * instead exercises {@code SolvikValues} and {@code SolvikHash} side by side across the cross product
 * of a hand-built population, so a kind whose hash rule fails to mirror its equality rule cannot hide
 * behind a missing example. It reads the two services that the runtime dispatches after static
 * analysis, which is exactly the layer where drift is possible.
 *
 * <p>The converse is deliberately not asserted: unequal values are permitted to share a hash, and
 * several entries in the population collide on purpose.
 */
public class SolvikHashInvariantTest {

    @Test
    public void equalValuesAlwaysHashAlikeAcrossEveryRuntimeKind() {
        List<Object> population = population();
        List<String> violations = new ArrayList<>();

        for (int i = 0; i < population.size(); i++) {
            for (int j = 0; j < population.size(); j++) {
                Object left = population.get(i);
                Object right = population.get(j);
                if (!SolvikValues.equal(left, right)) {
                    // The invariant constrains only equal values; unequal pairs carry no obligation.
                    continue;
                }
                int leftHash = SolvikHash.hash(left).intValue();
                int rightHash = SolvikHash.hash(right).intValue();
                if (leftHash != rightHash) {
                    violations.add(describe(left) + " == " + describe(right) + " but hashes " + leftHash + " vs " + rightHash);
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    public void hashIsDeterministicForARepeatedValue() {
        // A hash that changed between calls would break any index built on it, independent of equality.
        for (Object value : population()) {
            int first = SolvikHash.hash(value).intValue();
            int second = SolvikHash.hash(value).intValue();
            assertThat(second).as("hash of %s must be stable", describe(value)).isEqualTo(first);
        }
    }

    @Test
    public void everyValueKindOnTheClasspathIsRepresentedInThePopulation() {
        // Guards the test itself, derived from the compiled classes rather than from a list kept by
        // hand. A hand-maintained expectation is only as current as the last edit to it: dropping a
        // sample from the population while leaving its kind covered by another sample passes silently,
        // and a value kind added later would simply be absent from the expectation and never checked.
        // Enumerating the object package inverts the failure mode, so a new runtime value class fails
        // this test until the population covers it.
        List<String> sampled = new ArrayList<>();
        for (Object value : population()) {
            String kind = describe(value);
            if (!sampled.contains(kind)) {
                sampled.add(kind);
            }
        }

        List<String> missing = new ArrayList<>();
        for (String valueKind : valueKindsOnClasspath()) {
            if (!sampled.contains(valueKind)) {
                missing.add(valueKind);
            }
        }
        assertThat(missing).as("runtime value kinds with no representative in the invariant population").isEmpty();

        // The non-class kinds the two services dispatch on, which no classpath scan can name.
        assertThat(sampled).contains("null", "SolvikUnit", "Boolean", "Character", "Integer", "Long",
                "Byte", "Short", "Double", "Float", "String");
    }

    @Test
    public void theIdentityHashFallbackAgreesWithEachKindOwnRule() {
        // `super.hashCode()` uses the identity service. When its receiver is a user object it must be
        // the reference identity hash, which guest programs already pin. For any other kind there is no
        // identity answer to give: the fixed rule for that kind already decides both equality and hash,
        // so the service defers to the ordinary hash instead of inventing a second answer that could
        // contradict equality. That deferral is the whole fallback branch, and it is reachable here but
        // not from guest code, because `super` only ever exists inside a user class body.
        Object[] nonUserValues = {null, SolvikUnit.INSTANCE, Integer.valueOf(5), Long.valueOf(6L),
                Byte.valueOf((byte) 2), Short.valueOf((short) 3), Boolean.TRUE, Character.valueOf('z'),
                Double.valueOf(2.5), Float.valueOf(2.5f), "text", regex("a+"), new SolvikSet()};
        for (Object value : nonUserValues) {
            assertThat(SolvikHash.identityHash(value))
                            .as("identityHash of a non-user value must equal its ordinary semantic hash")
                            .isEqualTo(SolvikHash.hash(value));
        }

        // A user object must take the identity branch rather than the deferral, otherwise a
        // super.hashCode() with no inherited override would return its own override's answer, which is
        // the recursion the fallback exists to avoid.
        SolvikClass plainClass = new SolvikClass("Plain", List.of(), List.of());
        SolvikAny object = new SolvikAny(plainClass);
        assertThat(SolvikHash.identityHash(object).intValue())
                        .isEqualTo(System.identityHashCode(object));
        assertThat(SolvikHash.hash(object).intValue())
                        .as("a class with no overrides hashes by identity too")
                        .isEqualTo(System.identityHashCode(object));
    }

    /**
     * One representative per runtime kind, plus deliberate duplicates that are equal-but-distinct and
     * distinct-but-plausibly-colliding, so both directions of hash agreement are exercised.
     */
    private static List<Object> population() {
        List<Object> values = new ArrayList<>();

        // The unit value and null: both have fixed rules on both sides of the invariant.
        values.add(null);
        values.add(SolvikUnit.INSTANCE);

        // Boolean.
        values.add(Boolean.TRUE);
        values.add(Boolean.FALSE);

        // Character.
        values.add(Character.valueOf('a'));
        values.add(Character.valueOf('b'));

        // Integral scalars. Each boxed value is a distinct object, so equality must be by value.
        values.add(Integer.valueOf(7));
        values.add(Integer.valueOf(7));
        values.add(Integer.valueOf(8));
        values.add(Integer.valueOf(-1));
        values.add(Long.valueOf(9L));
        values.add(Long.valueOf(9L));
        values.add(Long.valueOf(10L));
        values.add(Byte.valueOf((byte) 3));
        values.add(Byte.valueOf((byte) 3));
        values.add(Short.valueOf((short) 4));
        values.add(Short.valueOf((short) 4));

        // Floating values, including the two cases where Solvik IEEE equality and boxed Java equality
        // disagree: 0.0 equals -0.0, and NaN is unequal to itself.
        values.add(Double.valueOf(1.5));
        values.add(Double.valueOf(1.5));
        values.add(Double.valueOf(0.0));
        values.add(Double.valueOf(-0.0));
        values.add(Double.valueOf(Double.NaN));
        values.add(Double.valueOf(Double.NaN));
        values.add(Double.valueOf(Double.POSITIVE_INFINITY));
        values.add(Double.valueOf(Double.NEGATIVE_INFINITY));
        values.add(Float.valueOf(1.5f));
        values.add(Float.valueOf(1.5f));
        values.add(Float.valueOf(0.0f));
        values.add(Float.valueOf(-0.0f));
        values.add(Float.valueOf(Float.NaN));

        // Strings, including equal content in distinct instances.
        values.add("abc");
        values.add(new String("abc".toCharArray()));
        values.add("xyz");
        values.add("");

        // Enum values: same variant with equal payloads must agree, a different variant must not.
        SolvikEnumVariant payloadVariant = variant("Pair", 2);
        SolvikEnumVariant emptyVariant = variant("Empty", 0);
        SolvikEnumVariant otherOwnerVariant = variant("Pair", 2, "Other");
        values.add(new SolvikEnumValue(payloadVariant, new Object[] {Integer.valueOf(1), "x"}));
        values.add(new SolvikEnumValue(payloadVariant, new Object[] {Integer.valueOf(1), "x"}));
        values.add(new SolvikEnumValue(payloadVariant, new Object[] {Integer.valueOf(1), "y"}));
        values.add(new SolvikEnumValue(emptyVariant, new Object[0]));
        values.add(new SolvikEnumValue(emptyVariant, new Object[0]));
        values.add(new SolvikEnumValue(otherOwnerVariant, new Object[] {Integer.valueOf(1), "x"}));
        // A payload holding a collection compares by reference, so its hash must follow identity too.
        SolvikList shared = new SolvikList(new Object[0], false);
        values.add(new SolvikEnumValue(payloadVariant, new Object[] {shared, "x"}));
        values.add(new SolvikEnumValue(payloadVariant, new Object[] {shared, "x"}));
        values.add(new SolvikEnumValue(payloadVariant, new Object[] {new SolvikList(new Object[0], false), "x"}));

        // Regex: equality is exact source text, so equal text in distinct instances must agree.
        values.add(regex("a+"));
        values.add(regex("a+"));
        values.add(regex("b*"));

        // RegexMatch snapshots: equal snapshots from separate finds must agree, and a non-participating
        // group must stay distinct from an empty one.
        SolvikRegexMatch firstMatch = regex("(a)(b)?").find("ab");
        SolvikRegexMatch secondMatch = regex("(a)(b)?").find("ab");
        values.add(firstMatch);
        values.add(secondMatch);
        values.add(regex("(a)(b)?").find("a"));
        values.add(regex("(c)").find("ab"));

        // Mutable collections hash by identity; duplicates of the same instance test stability, and two
        // distinct instances test that content similarity never leaks into the hash.
        SolvikList listA = new SolvikList(new Object[] {Integer.valueOf(1)}, false);
        SolvikList listB = new SolvikList(new Object[] {Integer.valueOf(1)}, false);
        values.add(listA);
        values.add(listA);
        values.add(listB);
        values.add(new SolvikSet());
        values.add(new SolvikMap());
        values.add(new SolvikStack());

        // A user-defined class instance with no overrides in its hierarchy: both services must fall
        // back to reference identity, and the pairing rule guarantees it never has only one override.
        // Two instances of one class are unequal, so their hashes are unconstrained, but each must be
        // stable and distinct instances must not be forced equal.
        SolvikClass plainClass = new SolvikClass("Plain", List.of(), List.of());
        SolvikAny objectA = new SolvikAny(plainClass);
        values.add(objectA);
        values.add(objectA);
        values.add(new SolvikAny(plainClass));

        return values;
    }

    private static SolvikRegex regex(String source) {
        return new SolvikRegex(source, Pattern.compile(source));
    }

    private static SolvikEnumVariant variant(String name, int valueCount) {
        return variant(name, valueCount, "Colors");
    }

    private static SolvikEnumVariant variant(String name, int valueCount, String ownerName) {
        SolvikEnumClass owner = new SolvikEnumClass(ownerName, new EnumType(ownerName));
        return new SolvikEnumVariant(owner, name, valueCount);
    }

    /** A stable, human-readable identity for an entry, used for failure messages and kind coverage. */
    private static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        return value.getClass().getSimpleName();
    }

    /**
     * Concrete classes in the runtime object package that can appear as a Solvik value. Compile-time
     * metadata, abstract bases, and the numeric ladder are excluded, so what remains must be sampled.
     */
    private static List<String> valueKindsOnClasspath() {
        // Metadata and infrastructure, never a guest value: SolvikClass, SolvikEnumClass,
        // SolvikEnumVariant, and SolvikRuntimeTypes. SolvikStaticCell joins them because a static
        // property read hands the guest the cell's current value, never the cell itself. Abstract bases
        // such as SolvikBuiltinCollection are filtered separately because they cannot be instantiated.
        List<String> metadata = List.of("SolvikClass", "SolvikEnumClass", "SolvikEnumVariant",
                        "SolvikRuntimeTypes", "SolvikStaticCell");
        List<String> found = new ArrayList<>();
        try {
            var location = SolvikValues.class.getProtectionDomain().getCodeSource().getLocation();
            java.nio.file.Path root = java.nio.file.Paths.get(location.toURI());
            java.nio.file.Path directory = root.resolve("org/solvik/truffle/object");
            try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.list(directory)) {
                for (java.nio.file.Path path : paths.toList()) {
                    String fileName = path.getFileName().toString();
                    if (!fileName.endsWith(".class") || fileName.contains("$")) {
                        continue;
                    }
                    String simpleName = fileName.substring(0, fileName.length() - ".class".length());
                    if (metadata.contains(simpleName)) {
                        continue;
                    }
                    Class<?> type = Class.forName("org.solvik.truffle.object." + simpleName);
                    // Only a public, concrete class can be a guest-visible value kind. This also filters
                    // Truffle's annotation-processor output for DynamicObject subclasses, which is
                    // generated as a package-private class rather than by name, so a legitimately named
                    // value class could never be skipped by accident.
                    if (!java.lang.reflect.Modifier.isPublic(type.getModifiers())
                            || java.lang.reflect.Modifier.isAbstract(type.getModifiers())
                            || type.isInterface() || type.isEnum()) {
                        continue;
                    }
                    found.add(simpleName);
                }
            }
        } catch (Exception failure) {
            throw new AssertionError("unable to enumerate runtime value kinds", failure);
        }
        assertThat(found).as("the scan must find the known value classes").isNotEmpty();
        return found;
    }
}
