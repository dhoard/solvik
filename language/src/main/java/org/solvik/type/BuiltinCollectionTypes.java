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
 * The four canonical built-in collection descriptors and the helpers to look them up by name
 * (docs/LANGUAGE_SPEC.md section 11): {@code List<T>}, {@code Set<T>}, {@code Map<K, V>}, and
 * {@code Stack<T>}.
 *
 * <p>The type-parameter instances are created once and shared by every descriptor that names them,
 * so a member slot referencing the element parameter is the same identity the analyzer substitutes
 * when a concrete {@code List<String>} binds it. Keeping the descriptors in one table avoids a
 * duplicated per-collection member table in the analyzer and lowering.
 */
public final class BuiltinCollectionTypes {

    private BuiltinCollectionTypes() {
    }

    // One TypeParameterType instance per declared parameter, shared with the member tables.
    private static final TypeParameterType LIST_T = new TypeParameterType("T");
    private static final TypeParameterType SET_T = new TypeParameterType("T");
    private static final TypeParameterType MAP_K = new TypeParameterType("K");
    private static final TypeParameterType MAP_V = new TypeParameterType("V");
    private static final TypeParameterType STACK_T = new TypeParameterType("T");

    /** The mutable, index-addressed {@code List<T>}. */
    public static final BuiltinCollectionType LIST = new BuiltinCollectionType(
            "List",
            List.of(LIST_T),
            Map.ofEntries(
                    entry("isEmpty", BooleanType.INSTANCE, true),
                    entry("size", IntType.INSTANCE, true),
                    entry("add", UnitType.INSTANCE, false, LIST_T),
                    entry("get", LIST_T, false, IntType.INSTANCE),
                    entry("removeAt", LIST_T, false, IntType.INSTANCE),
                    entry("set", UnitType.INSTANCE, false, IntType.INSTANCE, LIST_T),
                    entry("clear", UnitType.INSTANCE, false)));

    /** The mutable, uniqueness-ordered {@code Set<T>}. */
    public static final BuiltinCollectionType SET = new BuiltinCollectionType(
            "Set",
            List.of(SET_T),
            Map.ofEntries(
                    entry("isEmpty", BooleanType.INSTANCE, true),
                    entry("size", IntType.INSTANCE, true),
                    entry("add", BooleanType.INSTANCE, false, SET_T),
                    entry("contains", BooleanType.INSTANCE, false, SET_T),
                    entry("remove", BooleanType.INSTANCE, false, SET_T),
                    entry("clear", UnitType.INSTANCE, false)));

    /** The mutable {@code Map<K, V>}, with separate key and value type parameters. */
    public static final BuiltinCollectionType MAP = new BuiltinCollectionType(
            "Map",
            List.of(MAP_K, MAP_V),
            Map.ofEntries(
                    entry("isEmpty", BooleanType.INSTANCE, true),
                    entry("size", IntType.INSTANCE, true),
                    entry("put", UnitType.INSTANCE, false, MAP_K, MAP_V),
                    entry("get", MAP_V, false, MAP_K),
                    entry("containsKey", BooleanType.INSTANCE, false, MAP_K),
                    entry("remove", BooleanType.INSTANCE, false, MAP_K),
                    entry("clear", UnitType.INSTANCE, false)));

    /** The mutable last-in, first-out {@code Stack<T>}. */
    public static final BuiltinCollectionType STACK = new BuiltinCollectionType(
            "Stack",
            List.of(STACK_T),
            Map.ofEntries(
                    entry("isEmpty", BooleanType.INSTANCE, true),
                    entry("size", IntType.INSTANCE, true),
                    entry("push", UnitType.INSTANCE, false, STACK_T),
                    entry("peek", STACK_T, false),
                    entry("pop", STACK_T, false),
                    entry("clear", UnitType.INSTANCE, false)));

    private static Map.Entry<String, BuiltinCollectionMember> entry(String name, Type returnType, boolean property, Type... parameterTypes) {
        return Map.entry(name, new BuiltinCollectionMember(name, List.of(parameterTypes), returnType, property));
    }

    /** The canonical descriptor for a written collection name, or empty when it is not a collection. */
    public static Optional<BuiltinCollectionType> byName(String name) {
        return switch (name) {
            case "List" -> Optional.of(LIST);
            case "Set" -> Optional.of(SET);
            case "Map" -> Optional.of(MAP);
            case "Stack" -> Optional.of(STACK);
            default -> Optional.empty();
        };
    }
}
