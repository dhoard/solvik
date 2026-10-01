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
import org.solvik.type.FunctionType;
import org.solvik.type.InterfaceType;
import org.solvik.type.IntegerType;
import org.solvik.type.LongType;
import org.solvik.type.NothingType;
import org.solvik.type.NullType;
import org.solvik.type.NumberType;
import org.solvik.type.StringType;
import org.solvik.type.Type;
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

    // ---------------------------------------------------------------------------------------------
    // Function types (docs/LANGUAGE_SPEC.md section 6, "Structural identity and assignability")
    //
    // "The shared type join understands function types: for two same-arity function types each joined
    // parameter takes the more specific of the two when one is assignable to the other, and the joined
    // result is their nearest common result type. That joined function type is the least common
    // function supertype allowed by contravariant parameters and covariant results." and "When a
    // parameter pair is unrelated or the results have no unique join, no function-type join exists and
    // the ordinary join may still select a shared nominal supertype such as `Any`; a join never
    // introduces `Nothing`, a union, or an intersection in order to manufacture a function supertype."
    // ---------------------------------------------------------------------------------------------

    /** {@code Animal <- Dog}: the pair the specification's own variance example uses. */
    private static ClassType dog() {
        ClassType animal = new ClassType("Animal");
        ClassType dog = new ClassType("Dog");
        dog.resolveSuperType(animal);
        return dog;
    }

    private static ClassType animalOf(ClassType dog) {
        return (ClassType) dog.superType().orElseThrow();
    }

    private static FunctionType func(Type parameter, Type result) {
        return FunctionType.canonical(List.of(parameter), result);
    }

    /**
     * Two same-arity function types that are incomparable still have a least common function
     * supertype: the parameter pair contributes its more specific member (contravariance) and the
     * results contribute their nearest common supertype (covariance).
     */
    @Test
    public void incomparableFunctionTypesJoinToTheLeastCommonFunctionType() {
        ClassType dog = dog();
        ClassType animal = animalOf(dog);
        FunctionType narrower = func(dog, dog);
        FunctionType wider = func(animal, animal);
        FunctionType joined = func(dog, animal);
        assertThat(TypeJoin.leastCommonFunctionSupertype(narrower, wider)).isSameAs(joined);
        assertThat(TypeJoin.joinTypes(narrower, wider)).isSameAs(joined);
        assertThat(TypeJoin.joinTypes(wider, narrower)).as("the join is commutative").isSameAs(joined);
        assertThat(narrower.isAssignableTo(joined)).as("the join bounds the first input").isTrue();
        assertThat(wider.isAssignableTo(joined)).as("the join bounds the second input").isTrue();
    }

    /** A nullary pair has no parameters to reconcile, so only the results are joined. */
    @Test
    public void nullaryFunctionTypesJoinOnTheirResultsAlone() {
        ClassType dog = dog();
        ClassType animal = animalOf(dog);
        FunctionType joined = FunctionType.canonical(List.of(), animal);
        assertThat(TypeJoin.joinTypes(FunctionType.canonical(List.of(), dog), FunctionType.canonical(List.of(), animal)))
                .isSameAs(joined);
    }

    /**
     * When one function type is already assignable to the other, the supertype operand is the join;
     * the structural rule must not synthesize a third type for that case.
     */
    @Test
    public void anAssignablePairOfFunctionTypesJoinsToTheSupertypeOperand() {
        ClassType dog = dog();
        ClassType animal = animalOf(dog);
        FunctionType source = func(animal, dog);
        FunctionType target = func(dog, animal);
        assertThat(source.isAssignableTo(target)).isTrue();
        assertThat(TypeJoin.joinTypes(source, target)).isSameAs(target);
        assertThat(TypeJoin.joinTypes(target, source)).isSameAs(target);
    }

    /** An unrelated parameter pair has no function-type join, so the ordinary join selects `Any`. */
    @Test
    public void anUnrelatedParameterPairManufacturesNoFunctionSupertype() {
        FunctionType first = func(IntegerType.INSTANCE, StringType.INSTANCE);
        FunctionType second = func(new ClassType("Unrelated"), StringType.INSTANCE);
        assertThat(TypeJoin.leastCommonFunctionSupertype(first, second)).isNull();
        assertThat(TypeJoin.joinTypes(first, second)).isSameAs(AnyType.INSTANCE);
        assertThat(TypeJoin.joinTypes(second, first)).isSameAs(AnyType.INSTANCE);
    }

    /** Differing arities are not comparable at all, so the join is the nominal supertype `Any`. */
    @Test
    public void differingAritiesManufactureNoFunctionSupertype() {
        FunctionType unary = func(IntegerType.INSTANCE, StringType.INSTANCE);
        FunctionType binary = FunctionType.canonical(List.of(IntegerType.INSTANCE, IntegerType.INSTANCE), StringType.INSTANCE);
        assertThat(TypeJoin.leastCommonFunctionSupertype(unary, binary)).isNull();
        assertThat(TypeJoin.joinTypes(unary, binary)).isSameAs(AnyType.INSTANCE);
        assertThat(TypeJoin.joinTypes(binary, unary)).isSameAs(AnyType.INSTANCE);
    }

    /**
     * Results with no unique join leave no function-type join either; the ordinary join then still
     * finds `Any`, which is the shared nominal supertype the rule permits.
     */
    @Test
    public void resultsWithoutAUniqueJoinFallBackToTheNominalJoin() {
        InterfaceType first = new InterfaceType("First");
        InterfaceType second = new InterfaceType("Second");
        ClassType left = new ClassType("Left");
        ClassType right = new ClassType("Right");
        left.resolveInterfaceTypes(List.of(first, second));
        right.resolveInterfaceTypes(List.of(first, second));
        assertThat(TypeJoin.joinTypes(left, right)).as("the results themselves have no join").isNull();
        FunctionType firstFunction = func(IntegerType.INSTANCE, left);
        FunctionType secondFunction = func(IntegerType.INSTANCE, right);
        assertThat(TypeJoin.leastCommonFunctionSupertype(firstFunction, secondFunction)).isNull();
        assertThat(TypeJoin.joinTypes(firstFunction, secondFunction)).isSameAs(AnyType.INSTANCE);
    }

    /**
     * A function type written in a parameter position is reconciled by the same rule, and only by it:
     * the joined parameter is the more specific of the pair when one is assignable to the other.
     */
    @Test
    public void nestedFunctionTypeParametersJoinPositionwise() {
        ClassType dog = dog();
        ClassType animal = animalOf(dog);
        FunctionType innerSource = func(animal, dog);   // the subtype of the pair
        FunctionType innerTarget = func(dog, animal);
        assertThat(innerSource.isAssignableTo(innerTarget)).as("the nested pair is comparable").isTrue();
        FunctionType first = func(innerSource, IntegerType.INSTANCE);
        FunctionType second = func(innerTarget, IntegerType.INSTANCE);
        assertThat(TypeJoin.joinTypes(first, second))
                .isSameAs(FunctionType.canonical(List.of(innerSource), IntegerType.INSTANCE));
    }

    /**
     * Nested function types that are themselves incomparable leave no joined parameter, so no
     * function-type join exists and the ordinary join selects `Any`. The rule takes "the more specific
     * of the two when one is assignable to the other", and a nested pair with no such relation is the
     * unrelated case even though both parameters are function types.
     */
    @Test
    public void incomparableNestedFunctionTypeParametersJoinToAny() {
        ClassType dog = dog();
        ClassType animal = animalOf(dog);
        FunctionType first = func(func(dog, dog), IntegerType.INSTANCE);
        FunctionType second = func(func(animal, animal), IntegerType.INSTANCE);
        assertThat(TypeJoin.leastCommonFunctionSupertype(first, second)).isNull();
        assertThat(TypeJoin.joinTypes(first, second)).isSameAs(AnyType.INSTANCE);
    }

    /**
     * Results are joined by the shared service rather than by the more-specific rule, and the shared
     * service understands function types, so two incomparable nested results still contribute a joined
     * function type. This is the asymmetry the rule states: a parameter is taken from the pair only
     * when one member is assignable to the other, while a result takes the nearest common result type.
     */
    @Test
    public void nestedFunctionTypeResultsJoinThroughTheSharedService() {
        ClassType dog = dog();
        ClassType animal = animalOf(dog);
        FunctionType first = func(IntegerType.INSTANCE, func(dog, dog));
        FunctionType second = func(IntegerType.INSTANCE, func(animal, animal));
        assertThat(TypeJoin.joinTypes(first, second))
                .isSameAs(FunctionType.canonical(List.of(IntegerType.INSTANCE), func(dog, animal)));
        FunctionType comparable = func(IntegerType.INSTANCE, func(animal, dog));
        assertThat(TypeJoin.joinTypes(comparable, second))
                .as("an already comparable result pair takes the supertype result")
                .isSameAs(FunctionType.canonical(List.of(IntegerType.INSTANCE), func(animal, animal)));
    }

    /** A nullable branch makes the whole function-type join nullable, as it does any other type. */
    @Test
    public void aNullableFunctionTypeBranchMakesTheJoinNullable() {
        ClassType dog = dog();
        ClassType animal = animalOf(dog);
        FunctionType narrower = func(dog, dog);
        FunctionType wider = func(animal, animal);
        assertThat(TypeJoin.joinTypes(narrower, wider.nullableView())).isSameAs(func(dog, animal).nullableView());
        assertThat(TypeJoin.joinTypes(NullType.INSTANCE, narrower)).isSameAs(narrower.nullableView());
    }
}
