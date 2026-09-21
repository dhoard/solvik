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

import java.util.List;
import org.junit.jupiter.api.Test;
import org.solvik.type.AnyType;
import org.solvik.type.ClassType;
import org.solvik.type.InterfaceType;
import org.solvik.type.IntegerType;
import org.solvik.type.LongType;
import org.solvik.type.NothingType;
import org.solvik.type.NullType;
import org.solvik.type.NumberType;
import org.solvik.type.StringType;
import org.solvik.type.TypeJoin;

/**
 * Direct unit tests for the single shared declared-hierarchy join used by {@code match} and by block,
 * {@code if}, and {@code switch} expressions (docs/LANGUAGE_SPEC.md section 21.7). The branches
 * exercised here are not all reachable through source programs, so they are pinned directly.
 */
public final class SolvikTypeJoinTest {

    @Test
    public void anEmptySetHasNoJoin() {
        assertThat(TypeJoin.nearestCommonSupertype(List.of())).isNull();
    }

    @Test
    public void identicalTypesJoinToThemselves() {
        assertThat(TypeJoin.joinTypes(IntegerType.INSTANCE, IntegerType.INSTANCE)).isSameAs(IntegerType.INSTANCE);
        assertThat(TypeJoin.nearestCommonSupertype(List.of(StringType.INSTANCE, StringType.INSTANCE))).isSameAs(StringType.INSTANCE);
    }

    @Test
    public void nullJoinsInEitherPositionToANullableType() {
        assertThat(TypeJoin.joinTypes(NullType.INSTANCE, StringType.INSTANCE)).isSameAs(StringType.INSTANCE.nullableView());
        assertThat(TypeJoin.joinTypes(StringType.INSTANCE, NullType.INSTANCE)).isSameAs(StringType.INSTANCE.nullableView());
    }

    @Test
    public void numericSiblingsJoinToTheirNearestCommonSupertype() {
        assertThat(TypeJoin.joinTypes(IntegerType.INSTANCE, LongType.INSTANCE)).isSameAs(NumberType.INSTANCE);
        assertThat(TypeJoin.joinTypes(LongType.INSTANCE, IntegerType.INSTANCE)).isSameAs(NumberType.INSTANCE);
    }

    @Test
    public void unrelatedValueTypesJoinToAny() {
        assertThat(TypeJoin.joinTypes(IntegerType.INSTANCE, StringType.INSTANCE)).isSameAs(AnyType.INSTANCE);
    }

    @Test
    public void aUserClassAndAScalarJoinToAny() {
        ClassType user = new ClassType("User");
        assertThat(TypeJoin.joinTypes(user, IntegerType.INSTANCE)).isSameAs(AnyType.INSTANCE);
        assertThat(TypeJoin.joinTypes(IntegerType.INSTANCE, user)).isSameAs(AnyType.INSTANCE);
    }

    @Test
    public void unrelatedUserClassesJoinToAny() {
        ClassType first = new ClassType("First");
        ClassType second = new ClassType("Second");
        assertThat(TypeJoin.joinTypes(first, second)).isSameAs(AnyType.INSTANCE);
    }

    @Test
    public void nullableUnrelatedValuesJoinToNullableAny() {
        ClassType user = new ClassType("User");
        assertThat(TypeJoin.joinTypes(user, StringType.INSTANCE.nullableView())).isSameAs(AnyType.INSTANCE.nullableView());
        assertThat(TypeJoin.joinTypes(IntegerType.INSTANCE.nullableView(), user)).isSameAs(AnyType.INSTANCE.nullableView());
    }

    @Test
    public void aClassAndItsDirectInterfaceJoinToTheInterface() {
        InterfaceType named = new InterfaceType("Named");
        ClassType user = new ClassType("User");
        user.resolveInterfaceTypes(List.of(named));
        assertThat(TypeJoin.joinTypes(user, named)).isSameAs(named);
        assertThat(TypeJoin.joinTypes(named, user)).isSameAs(named);
    }

    @Test
    public void aClassAndAnUnrelatedInterfaceJoinToAny() {
        InterfaceType named = new InterfaceType("Named");
        InterfaceType aged = new InterfaceType("Aged");
        ClassType user = new ClassType("User");
        user.resolveInterfaceTypes(List.of(named));
        assertThat(TypeJoin.joinTypes(user, aged)).isSameAs(AnyType.INSTANCE);
        assertThat(TypeJoin.joinTypes(aged, user)).isSameAs(AnyType.INSTANCE);
    }

    @Test
    public void aSubtypeJoinsToItsSupertypeInEitherPosition() {
        assertThat(TypeJoin.joinTypes(IntegerType.INSTANCE, AnyType.INSTANCE)).isSameAs(AnyType.INSTANCE);
        assertThat(TypeJoin.joinTypes(AnyType.INSTANCE, IntegerType.INSTANCE)).isSameAs(AnyType.INSTANCE);
    }

    @Test
    public void aNullableBranchMakesTheWholeJoinNullable() {
        assertThat(TypeJoin.joinTypes(StringType.INSTANCE.nullableView(), StringType.INSTANCE)).isSameAs(StringType.INSTANCE.nullableView());
        assertThat(TypeJoin.joinTypes(IntegerType.INSTANCE, AnyType.INSTANCE.nullableView())).isSameAs(AnyType.INSTANCE.nullableView());
        assertThat(TypeJoin.joinTypes(NullType.INSTANCE, NullType.INSTANCE)).isSameAs(NullType.INSTANCE);
    }

    @Test
    public void nothingIsTheBottomTypeInAJoin() {
        assertThat(TypeJoin.joinTypes(NothingType.INSTANCE, StringType.INSTANCE)).isSameAs(StringType.INSTANCE);
        assertThat(TypeJoin.joinTypes(StringType.INSTANCE, NothingType.INSTANCE)).isSameAs(StringType.INSTANCE);
    }

    @Test
    public void incomparableMinimalInterfacesHaveNoJoin() {
        InterfaceType a = new InterfaceType("A");
        InterfaceType b = new InterfaceType("B");
        ClassType c = new ClassType("C");
        ClassType d = new ClassType("D");
        c.resolveInterfaceTypes(List.of(a, b));
        d.resolveInterfaceTypes(List.of(a, b));
        assertThat(TypeJoin.joinTypes(c, d)).isNull();
        assertThat(TypeJoin.nearestCommonSupertype(List.of(c, d))).isNull();
    }

    @Test
    public void aSingleCommonInterfaceIsTheJoin() {
        InterfaceType named = new InterfaceType("Named");
        ClassType c = new ClassType("C");
        ClassType d = new ClassType("D");
        c.resolveInterfaceTypes(List.of(named));
        d.resolveInterfaceTypes(List.of(named));
        assertThat(TypeJoin.joinTypes(c, d)).isSameAs(named);
    }

    @Test
    public void supertypesOfIsReflexiveAndTransitive() {
        assertThat(TypeJoin.supertypesOf(IntegerType.INSTANCE)).contains(IntegerType.INSTANCE, NumberType.INSTANCE, AnyType.INSTANCE);
        assertThat(TypeJoin.supertypesOf(AnyType.INSTANCE)).containsExactly(AnyType.INSTANCE);
    }
}
