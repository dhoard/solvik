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
import org.solvik.type.BooleanType;
import org.solvik.type.BuiltinCollectionTypes;
import org.solvik.type.ByteType;
import org.solvik.type.CharType;
import org.solvik.type.ClassType;
import org.solvik.type.DoubleType;
import org.solvik.type.FloatType;
import org.solvik.type.FunctionType;
import org.solvik.type.IntType;
import org.solvik.type.InterfaceType;
import org.solvik.type.LongType;
import org.solvik.type.NothingType;
import org.solvik.type.NullType;
import org.solvik.type.NullableType;
import org.solvik.type.NumberType;
import org.solvik.type.NumericTypes;
import org.solvik.type.ObjectType;
import org.solvik.type.ParameterizedType;
import org.solvik.type.RegexMatchType;
import org.solvik.type.RegexType;
import org.solvik.type.ShortType;
import org.solvik.type.StringType;
import org.solvik.type.Type;
import org.solvik.type.TypeEnvironment;
import org.solvik.type.TypeParameterType;
import org.solvik.type.UnitType;

/** Phase 4 tests for the explicit compiler type model and its built-in hierarchy metadata. */
public final class SolvikTypeModelTest {

    @Test
    public void allBuiltinsResolveByName() {
        TypeEnvironment environment = new TypeEnvironment();
        assertThat(environment.resolve("Any").orElseThrow()).isEqualTo(AnyType.INSTANCE);
        assertThat(environment.resolve("Object").orElseThrow()).isEqualTo(ObjectType.INSTANCE);
        assertThat(environment.resolve("Nothing").orElseThrow()).isEqualTo(NothingType.INSTANCE);
        assertThat(environment.resolve("Unit").orElseThrow()).isEqualTo(UnitType.INSTANCE);
        assertThat(environment.resolve("Boolean").orElseThrow()).isEqualTo(BooleanType.INSTANCE);
        assertThat(environment.resolve("String").orElseThrow()).isEqualTo(StringType.INSTANCE);
        assertThat(environment.resolve("Int").orElseThrow()).isEqualTo(IntType.INSTANCE);
        assertThat(environment.resolve("Number").orElseThrow()).isEqualTo(NumberType.INSTANCE);
        assertThat(environment.resolve("Byte").orElseThrow()).isEqualTo(ByteType.INSTANCE);
        assertThat(environment.resolve("Short").orElseThrow()).isEqualTo(ShortType.INSTANCE);
        assertThat(environment.resolve("Long").orElseThrow()).isEqualTo(LongType.INSTANCE);
        assertThat(environment.resolve("Float").orElseThrow()).isEqualTo(FloatType.INSTANCE);
        assertThat(environment.resolve("Double").orElseThrow()).isEqualTo(DoubleType.INSTANCE);
        assertThat(environment.resolve("Char").orElseThrow()).isEqualTo(CharType.INSTANCE);
        assertThat(environment.resolve("Regex").orElseThrow()).isEqualTo(RegexType.INSTANCE);
        assertThat(environment.resolve("RegexMatch").orElseThrow()).isEqualTo(RegexMatchType.INSTANCE);
        assertThat(environment.resolve("Widget").isEmpty()).isTrue();
        assertThat(//
                environment.builtins().stream().map(Type::name).toList()).isEqualTo(List.of("Any", "Object", "Nothing", "Number", "Byte", "Short", "Int", "Long", "Float", "Double", "Boolean", "Char", "String", "Unit", "Regex", "RegexMatch", "List", "Set", "Map", "Stack"));
    }

    @Test
    public void builtinsFormTheSpecifiedInitialHierarchy() {
        assertThat(IntType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE)).isTrue();
        assertThat(IntType.INSTANCE.isSubtypeOf(AnyType.INSTANCE)).isTrue();
        assertThat(BooleanType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE)).isTrue();
        assertThat(StringType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE)).isTrue();
        assertThat(UnitType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE)).isTrue();
        assertThat(ObjectType.INSTANCE.isSubtypeOf(AnyType.INSTANCE)).isTrue();
        // Reflexive.
        assertThat(IntType.INSTANCE.isSubtypeOf(IntType.INSTANCE)).isTrue();
        // Unrelated nominal types are not compatible.
        assertThat(IntType.INSTANCE.isSubtypeOf(StringType.INSTANCE)).isFalse();
        assertThat(StringType.INSTANCE.isSubtypeOf(IntType.INSTANCE)).isFalse();
        // Any is the top type but is not itself an Object.
        assertThat(AnyType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE)).isFalse();
    }

    @Test
    public void completeNumericHierarchyIsPresent() {
        for (Type numeric : List.<Type>of(ByteType.INSTANCE, ShortType.INSTANCE, IntType.INSTANCE, LongType.INSTANCE, FloatType.INSTANCE, DoubleType.INSTANCE)) {
            assertThat(numeric.isSubtypeOf(NumberType.INSTANCE)).as(numeric.name() + " must derive from Number").isTrue();
            assertThat(numeric.isSubtypeOf(ObjectType.INSTANCE)).as(numeric.name() + " must derive from Object").isTrue();
            assertThat(NumericTypes.isNumeric(numeric)).as(numeric.name() + " must be numeric").isTrue();
        }
        assertThat(NumberType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE)).isTrue();
        assertThat(NumericTypes.isNumeric(NumberType.INSTANCE)).as("Number itself has no values and is not a concrete numeric type").isFalse();
        assertThat(NumericTypes.isNumeric(CharType.INSTANCE)).as("Char is not a numeric type").isFalse();
        assertThat(CharType.INSTANCE.isSubtypeOf(ObjectType.INSTANCE)).isTrue();
        // Numeric siblings are not assignment-compatible with one another.
        assertThat(IntType.INSTANCE.isAssignableTo(LongType.INSTANCE)).isFalse();
        assertThat(LongType.INSTANCE.isAssignableTo(IntType.INSTANCE)).isFalse();
        assertThat(IntType.INSTANCE.isAssignableTo(DoubleType.INSTANCE)).isFalse();
        // Integral classification.
        assertThat(NumericTypes.isIntegral(ByteType.INSTANCE)).isTrue();
        assertThat(NumericTypes.isIntegral(ShortType.INSTANCE)).isTrue();
        assertThat(NumericTypes.isIntegral(IntType.INSTANCE)).isTrue();
        assertThat(NumericTypes.isIntegral(LongType.INSTANCE)).isTrue();
        assertThat(NumericTypes.isIntegral(FloatType.INSTANCE)).isFalse();
        assertThat(NumericTypes.isIntegral(DoubleType.INSTANCE)).isFalse();
        assertThat(NumericTypes.isFloating(FloatType.INSTANCE)).isTrue();
        assertThat(NumericTypes.isFloating(DoubleType.INSTANCE)).isTrue();
    }

    @Test
    public void nothingIsTheBottomType() {
        for (Type type : List.<Type>of(AnyType.INSTANCE, ObjectType.INSTANCE, UnitType.INSTANCE, //
                BooleanType.INSTANCE, StringType.INSTANCE, IntType.INSTANCE, NumberType.INSTANCE, //
                ByteType.INSTANCE, ShortType.INSTANCE, LongType.INSTANCE, FloatType.INSTANCE, //
                DoubleType.INSTANCE, CharType.INSTANCE)) {
            assertThat(NothingType.INSTANCE.isAssignableTo(type)).as("Nothing must be assignable to " + type.name()).isTrue();
        }
        assertThat(NothingType.INSTANCE.isBottom()).isTrue();
        assertThat(AnyType.INSTANCE.isBottom()).isFalse();
    }

    @Test
    public void interfaceTypesSitUnderObjectAndAreNominal() {
        InterfaceType named = new InterfaceType("Named");
        InterfaceType aged = new InterfaceType("Aged");
        InterfaceType extended = new InterfaceType("Extended");
        extended.resolveSuperInterfaceTypes(List.of(named));
        InterfaceType unrelated = new InterfaceType("Unrelated");

        assertThat(named.isSubtypeOf(ObjectType.INSTANCE)).isTrue();
        assertThat(named.isSubtypeOf(AnyType.INSTANCE)).isTrue();
        assertThat(ObjectType.INSTANCE.isSubtypeOf(named)).isFalse();
        // Unrelated interfaces are never assignment-compatible (docs/LANGUAGE_SPEC.md section 8).
        assertThat(named.isSubtypeOf(aged)).isFalse();
        assertThat(aged.isSubtypeOf(named)).isFalse();
        assertThat(unrelated.isSubtypeOf(named)).isFalse();
        // Extension is a nominal subtype edge, transited transitively.
        assertThat(extended.isSubtypeOf(named)).isTrue();
        assertThat(named.isSubtypeOf(extended)).isFalse();
        assertThat(extended.isSubtypeOf(ObjectType.INSTANCE)).isTrue();
        assertThat(extended.interfaceTypes()).isEqualTo(List.of(named));
        // A cycle in a malformed extension graph must terminate rather than recurse forever.
        InterfaceType left = new InterfaceType("Left");
        InterfaceType right = new InterfaceType("Right");
        left.resolveSuperInterfaceTypes(List.of(right));
        right.resolveSuperInterfaceTypes(List.of(left));
        assertThat(left.isSubtypeOf(left)).isTrue();
        assertThat(left.isSubtypeOf(right)).isTrue();
    }

    @Test
    public void classInterfaceEdgesJoinTheSubtypeRelation() {
        ClassType user = new ClassType("User");
        InterfaceType named = new InterfaceType("Named");
        InterfaceType aged = new InterfaceType("Aged");
        user.resolveInterfaceTypes(List.of(named, aged));
        assertThat(user.isSubtypeOf(named)).isTrue();
        assertThat(user.isSubtypeOf(aged)).isTrue();
        assertThat(user.isSubtypeOf(ObjectType.INSTANCE)).isTrue();
        assertThat(named.isSubtypeOf(user)).isFalse();
        // An interface inherited by an extended interface is still a supertype of the class.
        InterfaceType base = new InterfaceType("Base");
        InterfaceType derivedFace = new InterfaceType("DerivedFace");
        derivedFace.resolveSuperInterfaceTypes(List.of(base));
        ClassType implementation = new ClassType("Implementation");
        implementation.resolveInterfaceTypes(List.of(derivedFace));
        assertThat(implementation.isSubtypeOf(base)).isTrue();
        assertThat(implementation.isSubtypeOf(derivedFace)).isTrue();
        assertThat(base.isSubtypeOf(implementation)).isFalse();
    }

    @Test
    public void functionTypeCarriesParameterAndReturnTypes() {
        FunctionType type = new FunctionType(List.of(IntType.INSTANCE, StringType.INSTANCE), BooleanType.INSTANCE);
        assertThat(type.parameterTypes()).isEqualTo(List.of(IntType.INSTANCE, StringType.INSTANCE));
        assertThat(type.returnType()).isEqualTo(BooleanType.INSTANCE);
        assertThat(type.name()).isEqualTo("(Int, String) -> Boolean");
        assertThat(type.isSubtypeOf(type)).isTrue();
        FunctionType other = new FunctionType(List.of(IntType.INSTANCE, StringType.INSTANCE), BooleanType.INSTANCE);
        assertThat(type.isSubtypeOf(other)).as("distinct function types are distinct identities").isFalse();
    }

    @Test
    public void nullableTypesAreCanonicalAndExposeTheNonNullType() {
        Type stringNullable = StringType.INSTANCE.nullableView();
        assertThat(stringNullable instanceof NullableType).isTrue();
        assertThat(stringNullable.name()).isEqualTo("String?");
        assertThat(StringType.INSTANCE.nullableView()).as("the nullable view is canonical per type instance").isSameAs(stringNullable);
        assertThat(stringNullable.nullableView()).as("nullable views are never nested").isSameAs(stringNullable);
        assertThat(NullType.INSTANCE.nullableView()).isSameAs(NullType.INSTANCE);
        assertThat(stringNullable.nonNullType()).isEqualTo(StringType.INSTANCE);
        assertThat(stringNullable.isNullable()).isTrue();
        assertThat(StringType.INSTANCE.isNullable()).isFalse();
        assertThat(stringNullable.isBottom()).as("a class type is not registered as a nullable type").isFalse();
    }

    @Test
    public void nullableAssignabilityFollowsTheSpecification() {
        Type stringNullable = StringType.INSTANCE.nullableView();
        Type objectNullable = ObjectType.INSTANCE.nullableView();
        Type anyNullable = AnyType.INSTANCE.nullableView();
        // S is assignable to T? whenever S is assignable to T.
        assertThat(StringType.INSTANCE.isAssignableTo(stringNullable)).isTrue();
        assertThat(StringType.INSTANCE.isAssignableTo(objectNullable)).isTrue();
        assertThat(StringType.INSTANCE.isAssignableTo(anyNullable)).isTrue();
        // S? is assignable to T? whenever S is assignable to T.
        assertThat(stringNullable.isAssignableTo(objectNullable)).isTrue();
        assertThat(stringNullable.isAssignableTo(anyNullable)).isTrue();
        // S? is not assignable to non-null T.
        assertThat(stringNullable.isAssignableTo(StringType.INSTANCE)).isFalse();
        assertThat(stringNullable.isAssignableTo(ObjectType.INSTANCE)).isFalse();
        assertThat(stringNullable.isAssignableTo(AnyType.INSTANCE)).isFalse();
        // null is assignable only to nullable types.
        assertThat(NullType.INSTANCE.isAssignableTo(stringNullable)).isTrue();
        assertThat(NullType.INSTANCE.isAssignableTo(anyNullable)).isTrue();
        assertThat(NullType.INSTANCE.isAssignableTo(StringType.INSTANCE)).isFalse();
        assertThat(NullType.INSTANCE.isAssignableTo(AnyType.INSTANCE)).isFalse();
        // Nullable siblings are unrelated when their non-null forms are.
        assertThat(IntType.INSTANCE.nullableView().isAssignableTo(stringNullable)).isFalse();
        // The bottom type remains assignable to every type, nullable included.
        assertThat(NothingType.INSTANCE.isAssignableTo(stringNullable)).isTrue();
    }

    @Test
    public void listIsAGenericBuiltinWithInvariantArguments() {
        assertThat(BuiltinCollectionTypes.LIST.typeParameters().size()).isEqualTo(1);
        assertThat(BuiltinCollectionTypes.LIST.typeParameters().get(0).name()).isEqualTo("T");
        assertThat(environment().resolve("List").orElseThrow()).isSameAs(BuiltinCollectionTypes.LIST);

        Type stringList = BuiltinCollectionTypes.LIST.parameterizedView(List.of(StringType.INSTANCE));
        Type intList = BuiltinCollectionTypes.LIST.parameterizedView(List.of(IntType.INSTANCE));
        assertThat(stringList instanceof ParameterizedType).isTrue();
        assertThat(stringList.name()).isEqualTo("List<String>");
        assertThat(BuiltinCollectionTypes.LIST.parameterizedView(List.of(StringType.INSTANCE))).as("generic applications are canonical per argument list").isSameAs(stringList);
        assertThat(stringList.isAssignableTo(stringList)).isTrue();
        assertThat(stringList.isAssignableTo(intList)).as("type arguments are invariant").isFalse();
        assertThat(intList.isAssignableTo(stringList)).isFalse();
        assertThat(stringList.isAssignableTo(ObjectType.INSTANCE)).isTrue();
        assertThat(stringList.isAssignableTo(AnyType.INSTANCE)).isTrue();
        assertThat(NothingType.INSTANCE.isAssignableTo(stringList)).isTrue();
    }

    @Test
    public void typeParametersAreTopTypedAndSubstitutable() {
        TypeParameterType parameter = new TypeParameterType("T");
        assertThat(parameter.name()).isEqualTo("T");
        assertThat(parameter.substitute(java.util.Map.of())).isSameAs(parameter);
        assertThat(parameter.isSubtypeOf(AnyType.INSTANCE)).isTrue();
        assertThat(parameter.isSubtypeOf(ObjectType.INSTANCE)).isFalse();
        assertThat(parameter.isSubtypeOf(StringType.INSTANCE)).isFalse();
        assertThat(parameter.substitute(java.util.Map.of(parameter, StringType.INSTANCE))).isSameAs(StringType.INSTANCE);
    }

    private static TypeEnvironment environment() {
        return new TypeEnvironment();
    }
}
