/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.object;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;
import org.solvik.truffle.SolvikFunction;

/**
 * Runtime metadata for a Solvik class (docs/ARCHITECTURE.md "Classes"): its class identity, its
 * statically declared property layout, and its method and constructor call targets.
 *
 * <p>The property layout is fixed when the class is created and never changes. Every instance is
 * allocated with the shared empty root shape and then has exactly its declared properties added in
 * declaration order, so instances of one class share a stable Truffle shape and arbitrary
 * undeclared member insertion is impossible (docs/ARCHITECTURE.md "Objects and Truffle Shape").
 */
public final class SolvikClass {

    /**
     * The empty root shape of the {@link SolvikObject} layout. All Solvik objects are created with
     * it and transition through the same property-addition sequence, which is what keeps their
     * shapes stable and shareable.
     */
    private static final Shape ROOT_SHAPE = Shape.newBuilder().layout(SolvikObject.class, MethodHandles.lookup()).build();

    /**
     * Canonical per-name property keys. A subclass reuses its superclass's field names, so keys
     * built for the same name in different classes must be the identical object to keep a single
     * stable shape across the inheritance hierarchy.
     */
    private static final Map<String, TruffleString> PROPERTY_KEY_POOL = new ConcurrentHashMap<>();

    private final String name;
    private final String[] propertyNames;
    private final boolean[] propertyMutable;
    private final TruffleString[] propertyKeys;
    private final Map<String, Integer> propertyIndices = new HashMap<>();
    private final Map<String, SolvikFunction> methods = new HashMap<>();
    private SolvikFunction constructor;
    /** The runtime superclass, or {@code null} when the class derives directly from {@code Object}. */
    private SolvikClass superClass;
    /**
     * The names of every interface this class conforms to, including those inherited through the
     * superclass and those an interface extends. Interface identity is nominal, so names are enough
     * for a runtime type test; Phase 11 replaces this with reified generics when they exist.
     */
    private final Set<String> interfaceNames = new LinkedHashSet<>();

    public SolvikClass(String name, List<String> propertyNames, List<Boolean> propertyMutable) {
        this.name = Objects.requireNonNull(name);
        if (propertyNames.size() != propertyMutable.size()) {
            throw new IllegalArgumentException("property metadata length mismatch");
        }
        int count = propertyNames.size();
        this.propertyNames = new String[count];
        this.propertyMutable = new boolean[count];
        this.propertyKeys = new TruffleString[count];
        for (int i = 0; i < count; i++) {
            String propertyName = propertyNames.get(i);
            this.propertyNames[i] = propertyName;
            this.propertyMutable[i] = propertyMutable.get(i);
            this.propertyKeys[i] = canonicalKey(propertyName);
            this.propertyIndices.put(propertyName, i);
        }
    }

    /** The shared empty shape every Solvik object is allocated with. */
    public static Shape rootShape() {
        return ROOT_SHAPE;
    }

    @TruffleBoundary
    private static TruffleString canonicalKey(String name) {
        return PROPERTY_KEY_POOL.computeIfAbsent(name, n -> TruffleString.fromJavaStringUncached(n, TruffleString.Encoding.UTF_8));
    }

    public String name() {
        return name;
    }

    public int propertyCount() {
        return propertyNames.length;
    }

    public String propertyName(int index) {
        return propertyNames[index];
    }

    /** The Truffle property key of the field at {@code index}, stable for the class's lifetime. */
    public TruffleString propertyKey(int index) {
        return propertyKeys[index];
    }

    public boolean isPropertyMutable(int index) {
        return propertyMutable[index];
    }

    public int propertyIndex(String propertyName) {
        Integer index = propertyIndices.get(propertyName);
        if (index == null) {
            throw new IllegalArgumentException("undeclared property '" + propertyName + "'");
        }
        return index;
    }

    /** Installs a lowered method; called exactly once per method during lowering. */
    public void installMethod(String methodName, SolvikFunction method) {
        Objects.requireNonNull(methodName);
        Objects.requireNonNull(method);
        if (methods.put(methodName, method) != null) {
            throw new IllegalStateException("method '" + methodName + "' is already installed");
        }
    }

    /**
     * Looks up the virtual dispatch target for {@code methodName}. A boundary keeps the backing
     * map lookup out of runtime-compiled guest code, where the JDK map method is blocklisted.
     */
    @TruffleBoundary
    public SolvikFunction method(String methodName) {
        return methods.get(methodName);
    }

    /** Installs the runtime superclass; called exactly once per class during lowering. */
    public void setSuperClass(SolvikClass resolved) {
        if (this.superClass != null) {
            throw new IllegalStateException("superclass of '" + name + "' is already installed");
        }
        this.superClass = Objects.requireNonNull(resolved);
    }

    /** Records that instances of this class conform to the named interface. */
    public void addInterfaceName(String interfaceName) {
        interfaceNames.add(Objects.requireNonNull(interfaceName));
    }

    /** Whether this class is {@code other} or a subclass of it. */
    public boolean isSubclassOf(SolvikClass other) {
        for (SolvikClass current = this; current != null; current = current.superClass) {
            if (current == other) {
                return true;
            }
        }
        return false;
    }

    /** Whether instances of this class conform to the named interface, transitively. */
    @TruffleBoundary
    public boolean implementsInterface(String interfaceName) {
        return interfaceNames.contains(interfaceName);
    }

    /** Installs the lowered constructor; called exactly once during lowering. */
    public void installConstructor(SolvikFunction constructor) {
        if (this.constructor != null) {
            throw new IllegalStateException("constructor is already installed");
        }
        this.constructor = Objects.requireNonNull(constructor);
    }

    public SolvikFunction constructor() {
        if (constructor == null) {
            throw new IllegalStateException("constructor of '" + name + "' was used before lowering installed it");
        }
        return constructor;
    }

    @Override
    public String toString() {
        return name;
    }
}
