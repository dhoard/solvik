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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.solvik.type.AnyType;
import org.solvik.type.BooleanType;
import org.solvik.type.BuiltinCollectionTypes;
import org.solvik.type.ByteType;
import org.solvik.type.CharType;
import org.solvik.type.ClassType;
import org.solvik.type.DoubleType;
import org.solvik.type.FloatType;
import org.solvik.type.InterfaceType;
import org.solvik.type.IntType;
import org.solvik.type.LongType;
import org.solvik.type.NothingType;
import org.solvik.type.NullType;
import org.solvik.type.NumberType;
import org.solvik.type.RegexMatchType;
import org.solvik.type.RegexType;
import org.solvik.type.ShortType;
import org.solvik.type.StringType;
import org.solvik.type.Type;
import org.solvik.type.TypeJoin;
import org.solvik.type.UnitType;

/**
 * Algebraic invariants of the corrected {@code Any}-rooted type lattice. These are exhaustive over
 * a representative sample of built-in, nominal, parameterized, and nullable types rather than
 * pinning individual pairs, so the subtype relation and the shared join are checked as structures:
 * reflexivity, transitivity, antisymmetry, top/bottom, nullability, upper-bound, and
 * least-upper-bound behavior. A mistake in a single supertype edge or a join special case fails
 * these regardless of which pair happens to trip it.
 */
public final class SolvikTypeHierarchyPropertyTest {

    /** Every sample type wrapped in both its non-null and canonical nullable view. */
    private static List<Type> allTypes() {
        ClassType animal = new ClassType("Animal");
        ClassType dog = new ClassType("Dog");
        dog.resolveSuperType(animal);
        InterfaceType named = new InterfaceType("Named");
        InterfaceType aged = new InterfaceType("Aged");
        ClassType user = new ClassType("User");
        user.resolveInterfaceTypes(List.of(named, aged));
        Type stringList = BuiltinCollectionTypes.LIST.parameterizedView(List.of(StringType.INSTANCE));

        List<Type> bases = List.of( //
                AnyType.INSTANCE, NothingType.INSTANCE, NullType.INSTANCE, //
                NumberType.INSTANCE, ByteType.INSTANCE, ShortType.INSTANCE, IntType.INSTANCE, //
                LongType.INSTANCE, FloatType.INSTANCE, DoubleType.INSTANCE, //
                BooleanType.INSTANCE, CharType.INSTANCE, StringType.INSTANCE, UnitType.INSTANCE, //
                RegexType.INSTANCE, RegexMatchType.INSTANCE, //
                BuiltinCollectionTypes.LIST, BuiltinCollectionTypes.SET, //
                BuiltinCollectionTypes.MAP, BuiltinCollectionTypes.STACK, //
                animal, dog, named, aged, user, stringList);

        List<Type> all = new ArrayList<>();
        for (Type type : bases) {
            all.add(type);
            Type nullable = type.nullableView();
            if (nullable != type) {
                all.add(nullable);
            }
        }
        return all;
    }

    @Test
    public void everyNonNullValueTypeIsBelowAnyAndNullableTypesAreNot() {
        for (Type type : allTypes()) {
            if (type == NullType.INSTANCE) {
                assertThat(type.isSubtypeOf(AnyType.INSTANCE)).as("null is not below non-null Any").isFalse();
            } else if (type.isNullable()) {
                assertThat(type.isSubtypeOf(AnyType.INSTANCE)).as(type.name() + " is nullable and not below Any").isFalse();
            } else {
                assertThat(type.isSubtypeOf(AnyType.INSTANCE)).as(type.name() + " must be below Any").isTrue();
            }
        }
    }

    @Test
    public void nothingIsBelowEveryType() {
        for (Type type : allTypes()) {
            assertThat(NothingType.INSTANCE.isSubtypeOf(type)).as("Nothing must be below " + type.name()).isTrue();
        }
    }

    @Test
    public void anyIsNotBelowAnyOtherNonNullType() {
        for (Type type : allTypes()) {
            if (type.isNullable() || type == NullType.INSTANCE || type == AnyType.INSTANCE) {
                continue;
            }
            assertThat(AnyType.INSTANCE.isSubtypeOf(type)).as("Any must not be below " + type.name()).isFalse();
        }
    }

    @Test
    public void onlyNullAndNothingReachTheBottomTypes() {
        for (Type type : allTypes()) {
            if (type == NothingType.INSTANCE) {
                continue;
            }
            assertThat(type.isSubtypeOf(NothingType.INSTANCE)).as(type.name() + " must not be below Nothing").isFalse();
            boolean isNull = type == NullType.INSTANCE;
            assertThat(type.isSubtypeOf(NullType.INSTANCE)).as(type.name() + " is below Null only when it is Null").isEqualTo(isNull);
        }
    }

    @Test
    public void subtypeRelationIsReflexive() {
        for (Type type : allTypes()) {
            assertThat(type.isSubtypeOf(type)).as(type.name() + " must be below itself").isTrue();
        }
    }

    @Test
    public void subtypeRelationIsTransitive() {
        List<Type> types = allTypes();
        for (Type a : types) {
            for (Type b : types) {
                if (!a.isSubtypeOf(b)) {
                    continue;
                }
                for (Type c : types) {
                    if (b.isSubtypeOf(c)) {
                        assertThat(a.isSubtypeOf(c)).as(a.name() + " <: " + b.name() + " <: " + c.name() + " must imply " + a.name() + " <: " + c.name()).isTrue();
                    }
                }
            }
        }
    }

    @Test
    public void subtypeRelationIsAntisymmetric() {
        List<Type> types = allTypes();
        for (Type a : types) {
            for (Type b : types) {
                if (a.isSubtypeOf(b) && b.isSubtypeOf(a)) {
                    assertThat(a).as(a.name() + " and " + b.name() + " are mutually subtypes and must be identical").isSameAs(b);
                }
            }
        }
    }

    @Test
    public void everyValueTypeReachesAnyThroughItsSuperTypeChain() {
        for (Type type : allTypes()) {
            if (type == NothingType.INSTANCE || type == NullType.INSTANCE || type.isNullable()) {
                continue;
            }
            Set<Type> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            Type current = type;
            boolean reachedAny = false;
            while (current != null) {
                if (current == AnyType.INSTANCE) {
                    reachedAny = true;
                    break;
                }
                if (!seen.add(current)) {
                    break;
                }
                current = current.superType().orElse(null);
            }
            assertThat(reachedAny).as(type.name() + " must reach Any through superType()").isTrue();
        }
    }

    @Test
    public void nullableTypesFollowTheSpecifiedRules() {
        for (Type type : allTypes()) {
            if (type.isNullable() || type == NullType.INSTANCE) {
                continue;
            }
            Type nullable = type.nullableView();
            assertThat(type.isSubtypeOf(nullable)).as(type.name() + " must be assignable to " + nullable.name()).isTrue();
            assertThat(nullable.nonNullType()).as(nullable.name() + " must expose " + type.name()).isSameAs(type);
            assertThat(nullable.isSubtypeOf(type)).as(nullable.name() + " must not be assignable to non-null " + type.name()).isFalse();
            assertThat(NullType.INSTANCE.isSubtypeOf(nullable)).as("null must be assignable to " + nullable.name()).isTrue();
        }
    }

    @Test
    public void joinIsAnUpperBoundOfBothInputs() {
        List<Type> types = allTypes();
        for (Type a : types) {
            for (Type b : types) {
                Type join = TypeJoin.joinTypes(a, b);
                if (join == null) {
                    continue;
                }
                assertThat(a.isSubtypeOf(join)).as(a.name() + " must be below join(" + a.name() + ", " + b.name() + ") = " + join.name()).isTrue();
                assertThat(b.isSubtypeOf(join)).as(b.name() + " must be below join(" + a.name() + ", " + b.name() + ") = " + join.name()).isTrue();
            }
        }
    }

    @Test
    public void joinIsTheLeastUpperBoundOfNonNullInputs() {
        List<Type> types = allTypes();
        for (Type a : types) {
            if (a.isNullable() || a == NullType.INSTANCE) {
                continue;
            }
            for (Type b : types) {
                if (b.isNullable() || b == NullType.INSTANCE) {
                    continue;
                }
                Type join = TypeJoin.joinTypes(a, b);
                if (join == null) {
                    continue;
                }
                for (Type common : types) {
                    if (a.isSubtypeOf(common) && b.isSubtypeOf(common)) {
                        assertThat(join.isSubtypeOf(common)).as("join(" + a.name() + ", " + b.name() + ") = " + join.name() + " must be below common supertype " + common.name()).isTrue();
                    }
                }
            }
        }
    }

    @Test
    public void joinIsCommutativeAndIdempotent() {
        List<Type> types = allTypes();
        for (Type a : types) {
            assertThat(TypeJoin.joinTypes(a, a)).as("join(" + a.name() + ", itself)").isSameAs(a);
            for (Type b : types) {
                Type forward = TypeJoin.joinTypes(a, b);
                Type backward = TypeJoin.joinTypes(b, a);
                assertThat(forward).as("join must be commutative for " + a.name() + " and " + b.name()).isSameAs(backward);
            }
        }
    }

    @Test
    public void representativeRootChainsAreExact() {
        assertThat(AnyType.INSTANCE.superType()).isEmpty();
        assertThat(NothingType.INSTANCE.superType()).isEmpty();
        assertThat(NullType.INSTANCE.superType()).isEmpty();

        ClassType animal = new ClassType("Animal");
        ClassType dog = new ClassType("Dog");
        dog.resolveSuperType(animal);
        assertThat(dog.superType().orElseThrow()).isSameAs(animal);
        assertThat(animal.superType().orElseThrow()).isSameAs(AnyType.INSTANCE);

        InterfaceType named = new InterfaceType("Named");
        assertThat(named.superType().orElseThrow()).isSameAs(AnyType.INSTANCE);

        ClassType user = new ClassType("User");
        user.resolveInterfaceTypes(List.of(named));
        assertThat(user.superType().orElseThrow()).isSameAs(AnyType.INSTANCE);
        assertThat(user.interfaceTypes()).containsExactly(named);

        Type stringList = BuiltinCollectionTypes.LIST.parameterizedView(List.of(StringType.INSTANCE));
        assertThat(stringList.superType().orElseThrow()).isSameAs(AnyType.INSTANCE);
        assertThat(stringList.interfaceTypes()).isEmpty();
    }
}
