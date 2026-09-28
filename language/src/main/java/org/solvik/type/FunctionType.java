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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The compile-time type of a first-class function value, {@code func(P1, P2): R}.
 *
 * <p>Function types are the language-defined structural type family (docs/LANGUAGE_SPEC.md section
 * 11): they are compared structurally and are distinct from the nominal hierarchy that governs
 * user-defined classes and interfaces. Two function types are identical when they declare the same
 * parameters in the same order with identical types and an identical result; the declarations that
 * produced them play no role.
 *
 * <p>Function types are canonicalized so that identity-based compiler caches remain reliable:
 * {@link #canonical(List, Type)} returns the shared instance for structurally equal arguments. The
 * internal call signature recorded by {@link FunctionSymbol} is obtained through the same factory, so
 * a declared callable and the value written for it share one type.
 *
 * <p>Structural assignability is contravariant in the parameters and covariant in the result:
 * {@code func(S1, ..., Sn): SR} is assignable to {@code func(T1, ..., Tn): TR} exactly when {@code
 * Ti} is assignable to {@code Si} for every {@code i} and {@code SR} is assignable to {@code TR}.
 * Numeric widening is not a subtype relation and so is never applied here.
 */
public final class FunctionType extends Type {

    // Canonicalization is shared process-wide so that structurally equal function types are the
    // same instance. It is a concurrent map because Solvik Truffle contexts can run compilation
    // concurrently, and computeIfAbsent publishes each canonical instance exactly once.
    private static final Map<List<Type>, FunctionType> CACHE = new ConcurrentHashMap<>();

    private final List<Type> parameterTypes;
    private final Type returnType;

    private FunctionType(List<Type> parameterTypes, Type returnType, String name) {
        super(name);
        this.parameterTypes = List.copyOf(parameterTypes);
        this.returnType = Objects.requireNonNull(returnType);
    }

    /**
     * The canonical function type for the given parameters and result. Equal arguments return the
     * same instance, which keeps identity-based caches reliable and lets the recorded call signature
     * of a declared callable coincide with the type written for a first-class value of that
     * signature.
     */
    public static FunctionType canonical(List<Type> parameterTypes, Type returnType) {
        Objects.requireNonNull(parameterTypes, "parameterTypes");
        Objects.requireNonNull(returnType, "returnType");
        List<Type> parameters = List.copyOf(parameterTypes);
        String rendered = render(parameters, returnType);
        // Key the cache on the actual (canonical) parameter and result types, never on rendered
        // names: two distinct types can share a name, and the rendered spelling must never merge
        // them into one canonical instance.
        List<Type> keyComponents = new ArrayList<>(parameters.size() + 1);
        keyComponents.addAll(parameters);
        keyComponents.add(returnType);
        return CACHE.computeIfAbsent(keyComponents, key -> new FunctionType(parameters, returnType, rendered));
    }

    private static String render(List<Type> parameterTypes, Type returnType) {
        Objects.requireNonNull(parameterTypes);
        Objects.requireNonNull(returnType);
        StringBuilder sb = new StringBuilder("func(");
        for (int i = 0; i < parameterTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parameterTypes.get(i).name());
        }
        return sb.append("): ").append(returnType.name()).toString();
    }

    /** The parameter types, in source order; never empty, never null. */
    public List<Type> parameterTypes() {
        return parameterTypes;
    }

    /** The result type; never null. */
    public Type returnType() {
        return returnType;
    }

    /**
     * A function type has no nominal supertype but its only join with an unrelated type is {@code
     * Any}, the top of every value. Recording {@code Any} here (rather than leaving the declared
     * supertype empty) is what lets the nearest-common-supertype walk used by {@code if}, {@code
     * match}, and collection inference place two unrelated function types - and a function type and
     * any nominal type - at {@code Any} instead of reporting them as having no join.
     */
    @Override
    public Optional<Type> superType() {
        return Optional.of(AnyType.INSTANCE);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FunctionType functionType)) {
            return false;
        }
        return parameterTypes.equals(functionType.parameterTypes) && returnType.equals(functionType.returnType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterTypes, returnType);
    }

    @Override
    public boolean isSubtypeOf(Type other) {
        Objects.requireNonNull(other, "other");
        // A FunctionType receiver is always non-null: a nullable function value is a NullableType,
        // whose own isSubtypeOf (the nominal rule) handles the {@code T?} cases first.
        if (this == other) {
            return true;
        }
        if (other instanceof AnyType) {
            // Any is the top type of every non-null value, including function values.
            return true;
        }
        if (other instanceof NullableType otherNullable) {
            Type inner = otherNullable.inner();
            if (inner instanceof AnyType) {
                return true;
            }
            return inner instanceof FunctionType innerFunction && isAssignableStructurally(innerFunction);
        }
        return other instanceof FunctionType otherFunction && isAssignableStructurally(otherFunction);
    }

    /** Structural assignability of this (source) function type to {@code other} (target). */
    private boolean isAssignableStructurally(FunctionType other) {
        if (this.parameterTypes.size() != other.parameterTypes.size()) {
            return false;
        }
        for (int i = 0; i < this.parameterTypes.size(); i++) {
            // Contravariant in parameters: the target parameter must be assignable to this one.
            if (!other.parameterTypes.get(i).isAssignableTo(this.parameterTypes.get(i))) {
                return false;
            }
        }
        // Covariant in the result: this result must be assignable to the target result.
        return this.returnType.isAssignableTo(other.returnType);
    }

    @Override
    public Type substitute(Map<TypeParameterType, Type> mapping) {
        if (mapping.isEmpty()) {
            return this;
        }
        List<Type> substitutedParameters = new java.util.ArrayList<>(parameterTypes.size());
        for (Type parameter : parameterTypes) {
            substitutedParameters.add(parameter.substitute(mapping));
        }
        Type substitutedResult = returnType.substitute(mapping);
        return substitutedParameters.equals(parameterTypes) && substitutedResult == returnType ? this : canonical(substitutedParameters, substitutedResult);
    }
}
