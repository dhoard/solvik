/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The compile-time type of a declared type parameter, e.g. {@code T} in {@code class Box<T>}
 * (docs/LANGUAGE_SPEC.md section 11). Identity is nominal: one instance per declared parameter, so
 * two declarations' parameters are never the same type even when they share a name.
 *
 * <p>The initial language has no type-parameter bounds, so the only declared supertype of a type
 * parameter is {@code Any}. At a use site a type parameter is replaced before assignability is
 * decided; when the parameter is not replaced (a malformed call), treating it as {@code Any} keeps
 * checking from inventing a narrower relationship than the language guarantees.
 */
public final class TypeParameterType extends Type {

    public TypeParameterType(String name) {
        super(Objects.requireNonNull(name));
    }

    @Override
    public Optional<Type> superType() {
        return Optional.of(AnyType.INSTANCE);
    }

    @Override
    public Type substitute(Map<TypeParameterType, Type> mapping) {
        Type replacement = mapping.get(this);
        return replacement != null ? replacement : this;
    }
}
