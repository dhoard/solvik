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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The built-in type namespace: maps a written type name to its compile-time {@link Type}. Built-in
 * numeric/class hierarchy metadata lives here rather than in reflection over JVM classes
 * (docs/ARCHITECTURE.md "Type System").
 *
 * <p>The complete Phase 7 root hierarchy is predeclared, with Phase 14 adding the built-in
 * {@code Regex} and {@code RegexMatch} types: {@code Any} at the top, {@code Object} as the root of
 * value types, {@code Number} with its six numeric subtypes, {@code Boolean}, {@code Char},
 * {@code String}, {@code Unit}, {@code Regex}, {@code RegexMatch}, and the bottom type
 * {@code Nothing}. A class declaration registers its nominal {@link ClassType} here during semantic
 * collection so property, parameter, and return types may reference classes in any declaration
 * order.
 */
public final class TypeEnvironment {

    private final Map<String, Type> types = new LinkedHashMap<>();
    private final List<Type> builtins;

    public TypeEnvironment() {
        register(AnyType.INSTANCE);
        register(ObjectType.INSTANCE);
        register(NothingType.INSTANCE);
        register(NumberType.INSTANCE);
        register(ByteType.INSTANCE);
        register(ShortType.INSTANCE);
        register(IntType.INSTANCE);
        register(LongType.INSTANCE);
        register(FloatType.INSTANCE);
        register(DoubleType.INSTANCE);
        register(BooleanType.INSTANCE);
        register(CharType.INSTANCE);
        register(StringType.INSTANCE);
        register(UnitType.INSTANCE);
        register(RegexType.INSTANCE);
        register(RegexMatchType.INSTANCE);
        register(ListType.INSTANCE);
        this.builtins = List.copyOf(types.values());
    }

    private void register(Type type) {
        types.put(type.name(), type);
    }

    /**
     * Registers a user-declared nominal type. Used by semantic analysis when class declarations are
     * collected; built-in names must not be replaced.
     */
    public void declare(Type type) {
        types.put(type.name(), type);
    }

    /** Resolves a written type name to its type, or empty when no such type exists. */
    public Optional<Type> resolve(String name) {
        return Optional.ofNullable(types.get(name));
    }

    /** The predeclared built-in types in declaration order, for diagnostics and tests. */
    public List<Type> builtins() {
        return builtins;
    }
}
