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
import org.solvik.truffle.object.SolvikBuiltinCollection;
import org.solvik.truffle.object.SolvikEnumValue;
import org.solvik.truffle.object.SolvikAny;
import org.solvik.truffle.object.SolvikRegex;
import org.solvik.truffle.object.SolvikRegexMatch;

/**
 * The single definition of Solvik semantic equality ({@code ==}/{@code !=}) for the erased runtime
 * (docs/LANGUAGE_SPEC.md section 3).
 *
 * <p>This is the shared equality service every semantic-equality consumer routes through: the
 * {@link org.solvik.truffle.nodes.SolvikEqualNode}, explicit {@code equals} calls, the recursive
 * enum payload comparison, and the {@link org.solvik.truffle.object.SolvikSet}/{@link
 * org.solvik.truffle.object.SolvikMap} element and key scans. A user object dispatches its effective
 * {@code equals} override; every built-in scalar, {@code Unit}, enum, collection, and regex value
 * uses a fixed, non-overridable rule.
 *
 * <p>The left operand is the dynamic receiver. Equality is never delegated to arbitrary Java
 * {@code equals}: scalar leaves compare by IEEE value, enums recurse through this same service, and a
 * user override is invoked exactly once. The {@code ===}/{@code !==} identity operators do not use
 * this path at all.
 */
public final class SolvikValues {

    private SolvikValues() {
    }

    /**
     * Whether {@code left} equals {@code right} under the specification's Solvik equality rule
     * (docs/LANGUAGE_SPEC.md section 3). Both operands are evaluated by the caller; this compares
     * only the executed values. A null in either operand never invokes a user method.
     */
    @TruffleBoundary
    public static boolean equal(Object left, Object right) {
        // Null precheck: both null are equal, exactly one null is unequal, and no user code runs.
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }

        // Built-in scalar or Unit on the left: its fixed rule decides the result.
        if (isScalarOrUnit(left)) {
            return scalarOrUnitEqual(left, right);
        }

        // Enum value on the left: recursive value equality when the right is also an enum; a
        // different runtime kind is unequal (section 3).
        if (left instanceof SolvikEnumValue enumLeft) {
            return right instanceof SolvikEnumValue enumRight && enumLeft.valueEquals(enumRight);
        }

        // User-defined class instance on the left: dispatch its effective equals override. No class
        // in the hierarchy overrides it, the root default is reference identity.
        if (left instanceof SolvikAny object) {
            return dispatchUserEquals(object, right);
        }

        // Any other built-in value (collection, regex, match): its fixed rule.
        return builtInEqual(left, right);
    }

    /**
     * The fixed rule for a built-in scalar or {@code Unit} left value: same runtime kind and, for
     * scalars, IEEE value equality. A different runtime kind is unequal, so no implicit widening
     * ever makes an {@code Integer} equal a {@code Double}.
     */
    private static boolean scalarOrUnitEqual(Object left, Object right) {
        if (left == SolvikUnit.INSTANCE) {
            // Unit always equals Unit; there is exactly one Unit value.
            return right == SolvikUnit.INSTANCE;
        }
        if (left instanceof Boolean b) {
            return right instanceof Boolean rb && b.booleanValue() == rb.booleanValue();
        }
        if (left instanceof Character c) {
            return right instanceof Character rc && c.charValue() == rc.charValue();
        }
        if (left instanceof Byte by) {
            return right instanceof Byte rb && by.byteValue() == rb.byteValue();
        }
        if (left instanceof Short sh) {
            return right instanceof Short rs && sh.shortValue() == rs.shortValue();
        }
        if (left instanceof Integer in) {
            return right instanceof Integer ir && in.intValue() == ir.intValue();
        }
        if (left instanceof Long ln) {
            return right instanceof Long lr && ln.longValue() == lr.longValue();
        }
        if (left instanceof Float fl) {
            // IEEE 754 value comparison: NaN is unequal to itself, +0.0 equals -0.0, infinities compare by value.
            return right instanceof Float fr && fl.floatValue() == fr.floatValue();
        }
        if (left instanceof Double dl) {
            return right instanceof Double dr && dl.doubleValue() == dr.doubleValue();
        }
        if (left instanceof String s) {
            return right instanceof String rs && s.equals(rs);
        }
        // Unreachable for a statically checked program; every scalar is one of the cases above.
        throw new AssertionError("unknown scalar runtime kind: " + left.getClass());
    }

    /** Whether a runtime value is one of the built-in scalars or {@code Unit}. */
    private static boolean isScalarOrUnit(Object value) {
        return value instanceof Integer || value instanceof Long || value instanceof Byte
                || value instanceof Short || value instanceof Boolean || value instanceof Character
                || value instanceof Float || value instanceof Double || value instanceof String
                || value instanceof SolvikUnit;
    }

    /**
     * Dispatches the effective {@code equals(other)} override of a user object, or the reference
     * identity root default when no class in its hierarchy overrides the universal member. The
     * override is invoked even when both operands are the same reference; only the absence of an
     * override falls back to identity. Guest exceptions propagate normally.
     */
    private static boolean dispatchUserEquals(SolvikAny object, Object right) {
        // The effective equals override of the receiver's class hierarchy, or null for the root
        // default. The override is invoked even when both operands are the same reference.
        SolvikFunction override = object.solvikClass().findEqualsOverride();
        if (override == null) {
            // Root default: reference identity.
            return object == right;
        }
        Object[] arguments = new Object[2];
        arguments[0] = object;
        arguments[1] = right;
        // The override returns a boolean result; the call target erases it to Object, so it is cast.
        return (Boolean) override.callTarget().call(arguments);
    }

    /**
     * The fixed rule for every other built-in value: mutable collections compare by reference
     * identity, and {@code Regex} and {@code RegexMatch} compare by their source text and immutable
     * snapshot rather than by compiled-engine or caching identity.
     */
    private static boolean builtInEqual(Object left, Object right) {
        if (left instanceof SolvikBuiltinCollection) {
            return left == right;
        }
        if (left instanceof SolvikRegex regex) {
            return right instanceof SolvikRegex other && regex.source().equals(other.source());
        }
        if (left instanceof SolvikRegexMatch match) {
            return right instanceof SolvikRegexMatch other && matchSnapshotEqual(match, other);
        }
        // Unreachable for a statically checked program.
        throw new AssertionError("unknown built-in runtime kind: " + left.getClass());
    }

    /**
     * Compares two {@code RegexMatch} snapshots: start, end, group count, and every captured group
     * including null/nonparticipating groups. Comparison is null-safe so a missing group is distinct
     * from an empty one.
     */
    private static boolean matchSnapshotEqual(SolvikRegexMatch left, SolvikRegexMatch right) {
        if (left.start() != right.start() || left.end() != right.end() || left.groupCount() != right.groupCount()) {
            return false;
        }
        // Both operands are complete, valid snapshots, so every group index 0..groupCount is present.
        String[] leftGroups = left.snapshotGroups();
        String[] rightGroups = right.snapshotGroups();
        for (int i = 0; i <= left.groupCount(); i++) {
            if (!groupTextEqual(leftGroups[i], rightGroups[i])) {
                return false;
            }
        }
        return true;
    }

    /** Null-safe text comparison for a captured group: both null are equal, otherwise textual. */
    private static boolean groupTextEqual(String left, String right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }
}
