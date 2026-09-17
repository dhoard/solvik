/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
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
 * <p>The complete Phase 7 root hierarchy is predeclared: {@code Any} at the top, {@code Object} as
 * the root of value types, {@code Number} with its six numeric subtypes, {@code Boolean},
 * {@code Char}, {@code String}, {@code Unit}, and the bottom type {@code Nothing}. A class
 * declaration registers its nominal {@link ClassType} here during semantic collection so property,
 * parameter, and return types may reference classes in any declaration order.
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
