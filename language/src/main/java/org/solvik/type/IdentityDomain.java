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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The static identity-domain for the {@code ===} and {@code !==} reference-identity operators
 * (docs/LANGUAGE_SPEC.md section 3).
 *
 * <p>Only values that carry Solvik allocation identity may name an identity operator. Those are the
 * user-defined class types (including sealed classes and parameterized applications), the interface
 * types whose runtime values are class instances (including parameterized applications), the four
 * mutable built-in collections and their applications, and the nullable forms of any of these. Every
 * other type is not identity-bearing: the built-in scalars and {@code Unit}, enums, {@code Regex}
 * and {@code RegexMatch}, {@code Any}, unbounded type parameters, and the bottom and
 * null types. Internal Java-reference representation alone never makes allocation identity observable.
 *
 * <p>This class centralizes the classification so no visitor infers it from a Java implementation
 * class name. The set of identity-bearing nominal base types is assembled from the compiler's own
 * symbol tables, so a program's user types determine the domain at analysis time.
 */
public final class IdentityDomain {

    /** The nominal base types that admit {@code ===}/{\code !==} on their non-null values. */
    private final Set<Type> identityBases;

    private IdentityDomain(Set<Type> identityBases) {
        this.identityBases = Collections.unmodifiableSet(identityBases);
    }

    /**
     * Builds the domain from the compiler's declared class and interface types plus the built-in
     * collection base types. A type is identity-bearing when it is a nullable form of any base it
     * contains or of a generic application of one.
     */
    public static IdentityDomain forProgram(
            Iterable<? extends Type> classTypes, Iterable<? extends Type> interfaceTypes) {
        Set<Type> bases = new HashSet<>();
        for (Type type : classTypes) {
            bases.add(type);
        }
        for (Type type : interfaceTypes) {
            bases.add(type);
        }
        bases.add(BuiltinCollectionTypes.LIST);
        bases.add(BuiltinCollectionTypes.SET);
        bases.add(BuiltinCollectionTypes.MAP);
        bases.add(BuiltinCollectionTypes.STACK);
        return new IdentityDomain(bases);
    }

    /**
     * Whether a value of {@code type} carries Solvik allocation identity so it may name an identity
     * operator. A single level of {@link NullableType} is peeled: a nullable identity-bearing type is
     * identity-bearing only against a nullable identity-bearing operand.
     */
    public boolean isIdentityBearing(Type type) {
        Type peeled = type instanceof NullableType nullable ? nullable.inner() : type;
        if (peeled == NullType.INSTANCE || peeled == NothingType.INSTANCE) {
            return false;
        }
        // A generic application inherits its base's identity: List<Int> and Box<User> are
        // identity-bearing exactly when their nominal base is.
        if (peeled instanceof ParameterizedType parameterized) {
            peeled = parameterized.base();
        }
        return identityBases.contains(peeled);
    }

    /** The nominal base types classified as identity-bearing. Exposed for tests and diagnostics. */
    public Set<Type> bases() {
        return identityBases;
    }
}
