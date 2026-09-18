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
package org.solvik.type;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The single declared-hierarchy join used by every value-producing construct: {@code match} branch
 * results and block, {@code if}, and {@code switch} expression results all share this implementation
 * so their result typing cannot drift (docs/LANGUAGE_SPEC.md section 21).
 *
 * <p>The join never introduces a union type, numeric promotion, structural matching, or any type
 * that is not already part of the declared hierarchy. Nullability is folded in, so {@code null}
 * joined with {@code String} is {@code String?}. A set of types with no single nearest common
 * declared supertype (for example two classes whose only shared supertypes are incomparable
 * interfaces) has no join and is ill-typed.
 */
public final class TypeJoin {

    private TypeJoin() {
    }

    /**
     * The nearest common declared supertype of the given types, or {@code null} when the list is
     * empty or no single nearest supertype exists.
     */
    public static Type nearestCommonSupertype(List<Type> types) {
        if (types.isEmpty()) {
            return null;
        }
        Type result = types.get(0);
        for (int i = 1; i < types.size(); i++) {
            result = joinTypes(result, types.get(i));
            if (result == null) {
                return null;
            }
        }
        return result;
    }

    /** The join of two result types under the declared hierarchy; {@code null} when none exists. */
    public static Type joinTypes(Type a, Type b) {
        if (a == b) {
            return a;
        }
        if (a == NullType.INSTANCE) {
            return b.nullableView();
        }
        if (b == NullType.INSTANCE) {
            return a.nullableView();
        }
        boolean nullable = a.isNullable() || b.isNullable();
        Type left = a.nonNullType();
        Type right = b.nonNullType();
        Type joined;
        if (left == right) {
            joined = left;
        } else if (left.isAssignableTo(right)) {
            joined = right;
        } else if (right.isAssignableTo(left)) {
            joined = left;
        } else {
            Set<Type> leftSupers = supertypesOf(left);
            List<Type> common = new ArrayList<>();
            for (Type candidate : supertypesOf(right)) {
                if (leftSupers.contains(candidate)) {
                    common.add(candidate);
                }
            }
            Type minimal = null;
            for (Type candidate : common) {
                boolean mostSpecific = true;
                for (Type other : common) {
                    if (other != candidate && !candidate.isSubtypeOf(other)) {
                        mostSpecific = false;
                        break;
                    }
                }
                if (mostSpecific) {
                    if (minimal != null) {
                        return null;
                    }
                    minimal = candidate;
                }
            }
            joined = minimal;
        }
        if (joined == null) {
            return null;
        }
        return nullable ? joined.nullableView() : joined;
    }

    /** The reflexive-transitive closure of a type's declared supertypes and interface edges. */
    public static Set<Type> supertypesOf(Type type) {
        Set<Type> result = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<Type> frontier = new ArrayDeque<>();
        frontier.add(type);
        while (!frontier.isEmpty()) {
            Type current = frontier.removeFirst();
            if (!result.add(current)) {
                continue;
            }
            current.superType().ifPresent(frontier::addLast);
            for (Type face : current.interfaceTypes()) {
                frontier.addLast(face);
            }
        }
        return result;
    }
}
