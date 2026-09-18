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

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A final, mutable built-in generic collection: {@code List<T>}, {@code Set<T>}, {@code Map<K, V>},
 * or {@code Stack<T>} (docs/LANGUAGE_SPEC.md section 11). Each is a nominal generic type deriving
 * from {@code Any}, invariant in its type parameters, with a fixed member table describing its
 * computed properties and methods.
 *
 * <p>The member table is the single static descriptor shared by every collection kind. It lets the
 * analyzer resolve construction, member reads, and member calls through one path instead of
 * special-casing one type per collection. A collection's runtime representation is a specialized
 * {@code SolvikBuiltinCollection}; the type itself carries only the compile-time metadata.
 */
public final class BuiltinCollectionType extends Type {

    private final List<TypeParameterType> typeParameters;
    private final Map<String, BuiltinCollectionMember> members;

    BuiltinCollectionType(String name, List<TypeParameterType> typeParameters, Map<String, BuiltinCollectionMember> members) {
        super(name);
        this.typeParameters = List.copyOf(typeParameters);
        this.members = Map.copyOf(members);
    }

    /** The declared type parameters, in order. */
    @Override
    public List<TypeParameterType> typeParameters() {
        return typeParameters;
    }

    /** The member table: computed property or method by name. */
    public Map<String, BuiltinCollectionMember> members() {
        return members;
    }

    /** The type parameter at {@code index}, for substitution into a member's declared types. */
    public TypeParameterType typeParameter(int index) {
        return typeParameters.get(index);
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(AnyType.INSTANCE);
    }
}
