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
package org.solvik.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.solvik.truffle.object.SolvikAny;
import org.solvik.truffle.object.SolvikBuiltinCollection;
import org.solvik.truffle.object.SolvikEnumValue;
import org.solvik.truffle.object.SolvikRegex;
import org.solvik.truffle.object.SolvikRegexMatch;

/**
 * The single definition of the Solvik semantic hash ({@code hashCode}) for the erased runtime
 * (docs/LANGUAGE_SPEC.md section 3).
 *
 * <p>{@code SolvikHash} is the mirror of {@link SolvikValues}: it has the same shape, the same
 * dispatch rules, and the same fixed per-kind leaves, and it satisfies the invariant every hash-based
 * container depends on.
 *
 * <p><strong>Invariant.</strong> When {@code SolvikValues.equal(a, b)} is {@code true}, {@code hash(a)}
 * and {@code hash(b)} are the same {@code Integer}. Nothing requires the converse: two unequal values
 * may share a hash, and several cases below rely on that.
 *
 * <p>A user object dispatches its effective {@code hashCode} override, and the analyzer guarantees the
 * override exists exactly when an {@code equals} override exists in the same class, so the two
 * dispatches agree. When no class overrides them, both fall back to reference identity. Every built-in
 * kind uses a fixed rule derived from the same fields its equality rule reads, so consistency cannot
 * drift from equality.
 *
 * <p>Two leaves need care because Solvik equality is IEEE {@code ==} while boxed Java equality is not:
 *
 * <ul>
 *   <li>{@code 0.0} and {@code -0.0} are equal under {@code ==} but {@code Double.hashCode} differs, so
 *       negative zero is canonicalized to positive zero before hashing. This is the only observed
 *       place where Java's hash contradicts Solvik equality.</li>
 *   <li>{@code NaN} is unequal to itself but {@code Double.hashCode} agrees for two {@code NaN}s. That
 *       direction is permitted by the invariant, so {@code NaN} needs no special case and keeps the
 *       Java-derived hash.</li>
 * </ul>
 */
public final class SolvikHash {

    private SolvikHash() {
    }

    /**
     * The semantic hash of any Solvik value, including {@code null}. The result is a boxed
     * {@code Integer}, the erased representation every member return uses.
     */
    @TruffleBoundary
    public static Integer hash(Object value) {
        // A null hash is fixed, matching the null precheck in SolvikValues.equal: all null are equal.
        if (value == null) {
            return 0;
        }
        if (SolvikValues.isScalarOrUnit(value)) {
            return Integer.valueOf(scalarOrUnitHash(value));
        }
        if (value instanceof SolvikEnumValue enumValue) {
            return Integer.valueOf(enumHash(enumValue));
        }
        if (value instanceof SolvikAny object) {
            return userHash(object);
        }
        return Integer.valueOf(builtInHash(value));
    }

    /**
     * The fixed rule for a built-in scalar or {@code Unit} value, mirroring the leaf rules of
     * {@code SolvikValues.scalarOrUnitEqual}.
     */
    private static int scalarOrUnitHash(Object value) {
        if (value == SolvikUnit.INSTANCE) {
            // There is exactly one Unit value and it equals itself, so any fixed hash is consistent.
            return 1;
        }
        if (value instanceof Boolean b) {
            return b.booleanValue() ? 1231 : 1237;
        }
        if (value instanceof Character c) {
            return Character.hashCode(c.charValue());
        }
        if (value instanceof Byte by) {
            return Byte.hashCode(by.byteValue());
        }
        if (value instanceof Short sh) {
            return Short.hashCode(sh.shortValue());
        }
        if (value instanceof Integer in) {
            // Solvik integral equality is intValue() ==, identical to boxed Integer equality.
            return Integer.hashCode(in.intValue());
        }
        if (value instanceof Long ln) {
            return Long.hashCode(ln.longValue());
        }
        if (value instanceof Float fl) {
            return floatHash(fl.floatValue());
        }
        if (value instanceof Double dl) {
            return doubleHash(dl.doubleValue());
        }
        if (value instanceof String s) {
            // Solvik string equality is Java string equality, so Java's content hash agrees exactly.
            return s.hashCode();
        }
        throw new AssertionError("unknown scalar runtime kind: " + value.getClass());
    }

    /**
     * The hash of a {@code double}, consistent with Solvik's IEEE {@code ==} equality. Negative zero
     * is folded onto positive zero because {@code 0.0 == -0.0} while {@code Double.hashCode} separates
     * them. {@code NaN} keeps the Java-derived bits hash: it is unequal to itself, so the invariant
     * places no requirement on it.
     */
    private static int doubleHash(double value) {
        // Adding zero turns -0.0 into +0.0 and leaves every other value, including NaN, unchanged.
        double canonical = value + 0.0;
        return Double.hashCode(canonical);
    }

    /** The {@code float} counterpart of {@link #doubleHash}, folding {@code -0.0f} onto {@code 0.0f}. */
    private static int floatHash(float value) {
        float canonical = value + 0.0f;
        return Float.hashCode(canonical);
    }

    /**
     * The hash of an enum value: the variant identity mixed with the semantic hash of each payload,
     * mirroring the recursive payload comparison in {@code SolvikEnumValue.valueEquals}. Payloads use
     * this service rather than Java hashing so a payload holding a user object still agrees with
     * equality.
     */
    private static int enumHash(SolvikEnumValue value) {
        int result = System.identityHashCode(value.variant());
        for (int i = 0; i < value.valueCount(); i++) {
            result = 31 * result + hash(value.value(i)).intValue();
        }
        return result;
    }

    /**
     * The reference-identity hash of a receiver, used by {@code super.hashCode()} when no class in the
     * hierarchy supplies an override. For a user object this is exactly the same identity fallback
     * {@link #hash} uses, so it stays consistent with the {@code super.equals} identity default. For a
     * value that is not a user object the fixed rule for its kind already decides both equality and
     * hash, so this defers to {@link #hash} rather than inventing a second answer.
     */
    @TruffleBoundary
    public static Integer identityHash(Object value) {
        if (value instanceof SolvikAny) {
            return Integer.valueOf(System.identityHashCode(value));
        }
        return hash(value);
    }

    /**
     * Dispatches the effective {@code hashCode()} override of a user object, or the reference identity
     * root default when no class in its hierarchy overrides the universal member. Guest exceptions
     * propagate normally, matching the {@code equals} dispatch.
     *
     * <p>The analyzer rejects a class that overrides {@code equals} without also overriding
     * {@code hashCode}, and the reverse, so an object reaching the identity fallback here has no
     * {@code equals} override either and its equality is also reference identity.
     */
    private static Integer userHash(SolvikAny object) {
        SolvikFunction override = object.solvikClass().findHashCodeOverride();
        if (override == null) {
            // Root default: reference identity, mirroring the equals root default.
            return Integer.valueOf(System.identityHashCode(object));
        }
        Object result = override.callTarget().call(object);
        // The override returns an Integer; the call target erases it to Object, so it is cast.
        return (Integer) result;
    }

    /**
     * The fixed rule for every other built-in value, mirroring {@code SolvikValues.builtInEqual}.
     * Mutable collections compare by reference identity, so their hash is the identity hash.
     */
    private static int builtInHash(Object value) {
        if (value instanceof SolvikBuiltinCollection) {
            return System.identityHashCode(value);
        }
        if (value instanceof SolvikRegex regex) {
            // Equality is exact pattern source text.
            return regex.source().hashCode();
        }
        if (value instanceof SolvikRegexMatch match) {
            return matchSnapshotHash(match);
        }
        throw new AssertionError("unknown built-in runtime kind: " + value.getClass());
    }

    /**
     * The hash of a {@code RegexMatch} snapshot, reading exactly the fields the snapshot equality
     * compares: start, end, group count, and every captured group including a null group.
     */
    private static int matchSnapshotHash(SolvikRegexMatch match) {
        int result = match.start();
        result = 31 * result + match.end();
        result = 31 * result + match.groupCount();
        String[] groups = match.snapshotGroups();
        for (String group : groups) {
            // A null group must hash distinctly from an empty one, as the equality rule requires.
            result = 31 * result + (group == null ? 0 : group.hashCode());
        }
        return result;
    }

}
