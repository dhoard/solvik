/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A generic type application such as {@code List<String>} or {@code Box<User>}
 * (docs/LANGUAGE_SPEC.md section 11): a nominal generic type together with its type arguments.
 *
 * <p>Type arguments are invariant, so two applications are assignment-compatible only when they have
 * the same base type and pairwise identical arguments. Inheritance is still honored: the declared
 * superclass and interface edges of the base are substituted with this application's arguments, so
 * {@code class MyList<T> implements List<T>} makes {@code MyList<String>} a subtype of
 * {@code List<String>}. Instances are canonical per base and argument list through
 * {@link Type#parameterizedView(List)}, which keeps the identity-based subtype walk correct.
 */
public final class ParameterizedType extends Type {

    private final Type base;
    private final List<Type> arguments;

    ParameterizedType(Type base, List<Type> arguments) {
        super(render(base, arguments));
        this.base = Objects.requireNonNull(base);
        this.arguments = List.copyOf(arguments);
        List<TypeParameterType> parameters = base.typeParameters();
        if (parameters.size() != this.arguments.size()) {
            throw new IllegalArgumentException("type argument count does not match " + base.name());
        }
    }

    /** The nominal generic type being applied; a {@link ClassType} or {@link InterfaceType}. */
    public Type base() {
        return base;
    }

    /** The type arguments, in declaration order. */
    public List<Type> arguments() {
        return arguments;
    }

    /** The substitution that replaces the base's type parameters with this application's arguments. */
    public Map<TypeParameterType, Type> substitution() {
        List<TypeParameterType> parameters = base.typeParameters();
        Map<TypeParameterType, Type> map = new IdentityHashMap<>();
        for (int i = 0; i < parameters.size(); i++) {
            map.put(parameters.get(i), arguments.get(i));
        }
        return map;
    }

    @Override
    public Optional<Type> superType() {
        return base.superType().map(parent -> parent.substitute(substitution()));
    }

    @Override
    public List<Type> interfaceTypes() {
        List<Type> substituted = new ArrayList<>();
        for (Type face : base.interfaceTypes()) {
            substituted.add(face.substitute(substitution()));
        }
        return List.copyOf(substituted);
    }

    @Override
    public Type substitute(Map<TypeParameterType, Type> mapping) {
        List<Type> substituted = new ArrayList<>(arguments.size());
        boolean changed = false;
        for (Type argument : arguments) {
            Type replacement = argument.substitute(mapping);
            substituted.add(replacement);
            changed |= replacement != argument;
        }
        return changed ? base.parameterizedView(substituted) : this;
    }

    private static String render(Type base, List<Type> arguments) {
        Objects.requireNonNull(base);
        Objects.requireNonNull(arguments);
        StringBuilder sb = new StringBuilder(base.name()).append('<');
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(arguments.get(i).name());
        }
        return sb.append('>').toString();
    }
}
