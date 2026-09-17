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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A compile-time Solvik type (docs/ARCHITECTURE.md "Type System").
 *
 * <p>This is an explicit compiler type model, not a wrapper around JVM classes. Type identity is
 * nominal: the built-in types are singletons, and user-declared nominal types become identity
 * singletons when class and interface declarations are collected.
 *
 * <p>The subtype relation is reflexive, transitive along {@link #superType()} and the declared
 * {@link #interfaceTypes()} edges, and makes the bottom type {@code Nothing} a subtype of every
 * type. The top type is {@code Any}. Interfaces join the relation from Phase 8: a class is a
 * subtype of every interface it implements transitively, and an interface is a subtype of every
 * interface it extends. Phase 10 adds nullability: {@code S} is assignable to {@code T?} whenever
 * {@code S} is assignable to {@code T}, {@code S?} is assignable to {@code T?} whenever
 * {@code S} is assignable to {@code T}, and a nullable type is never assignable to a non-null type.
 * Phase 11 adds nominal generics: a generic type application is a {@link ParameterizedType} with
 * invariant type arguments, and a declared type parameter is a {@link TypeParameterType}. Enums
 * remain deliberately absent until their phase.
 */
public abstract class Type {

    private final String name;
    /** The canonical nullable view of this type, created on first use and stable thereafter. */
    private Type nullableView;
    /** Canonical generic applications of this type, keyed by their argument list. */
    private Map<List<Type>, Type> parameterizedViews;

    protected Type(String name) {
        this.name = Objects.requireNonNull(name);
    }

    /** The user-visible name; for built-ins this is the spelling usable as a type reference. */
    public final String name() {
        return name;
    }

    /** The unique direct supertype, or empty for the top and bottom types. */
    public Optional<Type> superType() {
        return Optional.empty();
    }

    /**
     * The interfaces declared directly on this type: a class's {@code implements} list or an
     * interface's {@code extends} list. Built-in and non-nominal types declare none.
     */
    public List<Type> interfaceTypes() {
        return List.of();
    }

    /** Whether this is the bottom type {@code Nothing}, a subtype of every type. */
    public boolean isBottom() {
        return false;
    }

    /**
     * The type parameters this type declares, in order. Only a generic class or interface declares
     * any; every other type has none. A bare reference to a type with parameters is a raw type and is
     * rejected during type resolution.
     */
    public List<TypeParameterType> typeParameters() {
        return List.of();
    }

    /**
     * The canonical generic application of this type to {@code arguments}. The result is cached per
     * distinct argument list so the same written application always yields the identical type, which
     * keeps identity comparison and the subtype walk reliable.
     */
    public final synchronized Type parameterizedView(List<Type> arguments) {
        Objects.requireNonNull(arguments, "arguments");
        if (typeParameters().isEmpty()) {
            throw new IllegalArgumentException(name + " is not generic");
        }
        if (parameterizedViews == null) {
            parameterizedViews = new HashMap<>();
        }
        return parameterizedViews.computeIfAbsent(List.copyOf(arguments), args -> new ParameterizedType(this, args));
    }

    /**
     * Replaces every declared type parameter in this type with its binding in {@code mapping}. The
     * default returns this type unchanged; {@link TypeParameterType}, {@link ParameterizedType}, and
     * {@link NullableType} override it so substitution reaches nested applications.
     */
    public Type substitute(Map<TypeParameterType, Type> mapping) {
        return this;
    }

    /**
     * Whether this type admits {@code null}. {@link NullableType} and {@link NullType} do; every
     * other type is non-null. Flow analysis uses this to decide whether narrowing is meaningful.
     */
    public boolean isNullable() {
        return false;
    }

    /**
     * The non-null type this type refines to after a null check. A non-null type is its own non-null
     * form; a nullable type yields its inner type; {@code Null} yields itself because it has no
     * non-null form.
     */
    public Type nonNullType() {
        return this;
    }

    /**
     * The canonical nullable type {@code T?} for this type. Returns this type unchanged when it is
     * already nullable or is {@code Null}, so a nullable type is never nested. The result is cached
     * per instance, which keeps identity comparison reliable for nullable spelled types and for the
     * assigned types recorded during narrowing.
     */
    public final synchronized Type nullableView() {
        if (this instanceof NullableType || this instanceof NullType) {
            return this;
        }
        Type view = nullableView;
        if (view == null) {
            view = new NullableType(this);
            nullableView = view;
        }
        return view;
    }

    /** Whether a value of this type may be used where {@code other} is required. */
    public final boolean isAssignableTo(Type other) {
        return isSubtypeOf(other);
    }

    /**
     * Whether this type is a subtype of {@code other} under the nominal hierarchy, walked over both
     * the single-inheritance superclass chain and the declared interface edges. The walk is
     * cycle-safe so a malformed declaration graph cannot loop forever.
     */
    public final boolean isSubtypeOf(Type other) {
        Objects.requireNonNull(other, "other");
        if (this == other || isBottom()) {
            return true;
        }
        if (other instanceof NullableType otherNullable) {
            // Assignability to T? ignores nullability on the source: null is a value of every
            // nullable type, S is assignable when S is a subtype of T, and S? reduces to S.
            if (this instanceof NullType) {
                return true;
            }
            Type source = this instanceof NullableType nullableSource ? nullableSource.inner() : this;
            return source.isSubtypeOf(otherNullable.inner());
        }
        if (this instanceof NullableType || this instanceof NullType) {
            // A type that admits null is never assignable to a non-null type.
            return false;
        }
        Map<Type, Boolean> visited = new IdentityHashMap<>();
        visited.put(this, Boolean.TRUE);
        return reaches(other, visited);
    }

    private boolean reaches(Type other, Map<Type, Boolean> visited) {
        for (Type parent : immediateSupertypes()) {
            if (parent == other) {
                return true;
            }
            if (visited.put(parent, Boolean.TRUE) == null && parent.reaches(other, visited)) {
                return true;
            }
        }
        return false;
    }

    private List<Type> immediateSupertypes() {
        List<Type> parents = new ArrayList<>(interfaceTypes());
        superType().ifPresent(parents::add);
        return parents;
    }

    @Override
    public final String toString() {
        return name;
    }
}
