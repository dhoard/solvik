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
     * The empty root shape of the {@link SolvikAny} layout. All Solvik objects are created with
     * it and transition through the same property-addition sequence, which is what keeps their
     * shapes stable and shareable.
     */
    private static final Shape ROOT_SHAPE = Shape.newBuilder().layout(SolvikAny.class, MethodHandles.lookup()).build();

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
    /**
     * Class-level storage for {@code static} properties, keyed by member name. Statics are never
     * merged into {@link #methods}: they belong to the declaring class only, are not inherited, and
     * must not enter the virtual dispatch table (docs/LANGUAGE_SPEC.md section 7). A static method
     * needs no entry here either: lowering binds a static call to its declaring class's implementation
     * directly, so a static method never participates in run-time lookup.
     */
    private final Map<String, SolvikStaticCell> staticCells = new HashMap<>();
    /** The class initializer ({@code <clinit>}) lowered for this class, or {@code null} when it has none. */
    private SolvikFunction classInitializer;
    /** Whether this class's initializer has run to completion. Volatile so it is safely published to
     *  any other thread that reaches the same runtime class. */
    private volatile boolean classInitialized;
    /**
     * Whether this class's initializer is on the run-time stack. Solvik is single-threaded, so an
     * initializer that (transitively) reaches its own class sees this flag and returns immediately,
     * which is how an initialization cycle observes zero-valued cells instead of looping forever
     * (docs/LANGUAGE_SPEC.md section 7).
     */
    private boolean classInitializing;
    private SolvikFunction constructor;
    /** The runtime superclass, or {@code null} when the class derives directly from {@code Any}. */
    private SolvikClass superClass;
    /**
     * The names of every interface this class conforms to, including those inherited through the
     * superclass and those an interface extends. Interface identity is nominal, so names are enough
     * for a runtime type test; Phase 11 replaces this with reified generics when they exist.
     */
    private final Set<String> interfaceNames = new LinkedHashSet<>();

    /** The name of the universal {@code equals} member, shared with the semantic-equality service. */
    public static final String EQUALS_NAME = "equals";

    /**
     * The name of the universal {@code hashCode} member, shared with the semantic-hash service. The
     * analyzer requires the two members to be overridden by the same class, so a class that supplies
     * an {@code equals} override always supplies a {@code hashCode} override at the same level of the
     * hierarchy (docs/LANGUAGE_SPEC.md section 3).
     */
    public static final String HASHCODE_NAME = "hashCode";

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

    /**
     * Walks the class hierarchy to the most-derived class that installs {@code equals} and returns
     * its effective override target, or {@code null} when no class in the hierarchy overrides the
     * universal member. The root default (reference identity) is signaled by {@code null}, so the
     * caller runs its own identity comparison instead of a Java method. A boundary keeps the map
     * lookups out of runtime-compiled guest code.
     */
    @TruffleBoundary
    public SolvikFunction findEqualsOverride() {
        SolvikClass current = this;
        while (current != null) {
            SolvikFunction override = current.method(EQUALS_NAME);
            if (override != null) {
                return override;
            }
            current = current.superClass;
        }
        return null;
    }

    /**
     * Walks the class hierarchy to the most-derived class that installs {@code hashCode} and returns
     * its effective override target, or {@code null} when no class in the hierarchy overrides the
     * universal member, in which case the caller uses reference identity. The analyzer guarantees that
     * an {@code equals} override and a {@code hashCode} override are declared by the same class, so
     * this never resolves further up the hierarchy than {@link #findEqualsOverride()}.
     */
    @TruffleBoundary
    public SolvikFunction findHashCodeOverride() {
        SolvikClass current = this;
        while (current != null) {
            SolvikFunction override = current.method(HASHCODE_NAME);
            if (override != null) {
                return override;
            }
            current = current.superClass;
        }
        return null;
    }

    /**
     * Installs the storage cell for one {@code static} property. Called exactly once per static
     * property during lowering; a class-level member shares the class's member namespace with every
     * other member, so a repeated name is an internal inconsistency.
     */
    public void installStaticCell(String memberName, SolvikStaticCell cell) {
        Objects.requireNonNull(memberName);
        Objects.requireNonNull(cell);
        if (staticCells.put(memberName, cell) != null) {
            throw new IllegalStateException("static property '" + memberName + "' is already installed");
        }
    }

    /** The storage cell of a {@code static} property, or {@code null} when the class declares none. */
    public SolvikStaticCell staticCell(String memberName) {
        return staticCells.get(memberName);
    }

    /**
     * Installs the lowered class initializer body ({@code <clinit>}: the static property declaration
     * initializers in source order, then the class initializer block). Called exactly once per class
     * that has any static initialization, during lowering.
     */
    public void installClassInitializer(SolvikFunction initializer) {
        Objects.requireNonNull(initializer);
        if (this.classInitializer != null) {
            throw new IllegalStateException("class initializer of '" + name + "' is already installed");
        }
        this.classInitializer = initializer;
    }

    /**
     * Initializes this class on its first active use, after its superclass chain
     * (docs/LANGUAGE_SPEC.md section 7). An already initialized or in-progress class returns
     * immediately: initialization runs at most once per class per program run, and an initializer
     * cycle observes the still-default cells rather than recursing, matching the initialization
     * semantics this language's static members are specified against.
     *
     * <p>A boundary keeps the superclass walk and the initializer call out of runtime-compiled guest
     * code; the hot steady-state answer after initialization is a single boolean field test that the
     * caller performs before entering this method.
     */
    @TruffleBoundary
    public void initializeClass() {
        if (classInitialized || classInitializing) {
            return;
        }
        classInitializing = true;
        try {
            if (superClass != null) {
                superClass.initializeClass();
            }
            SolvikFunction initializer = classInitializer;
            if (initializer != null) {
                initializer.callTarget().call(new Object[0]);
            }
            classInitialized = true;
        } finally {
            classInitializing = false;
        }
    }

    /**
     * Runs this class's initializer on first active use, and nothing afterwards. The steady-state test
     * is deliberately kept out of the boundary so the repeated case stays one field read in compiled
     * guest code (docs/LANGUAGE_SPEC.md section 7).
     */
    public void ensureInitialized() {
        if (!classInitialized) {
            initializeClass();
        }
    }

    /** Whether the caller may read a static cell without entering {@link #initializeClass()}. */
    public boolean classInitialized() {
        return classInitialized;
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
