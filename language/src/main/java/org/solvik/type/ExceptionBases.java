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
import java.util.Map;
import java.util.Set;

/**
 * The three predeclared guest exception base types (docs/LANGUAGE_SPEC.md error-handling phases):
 * {@code Exception} at the root, with {@code RuntimeException} and {@code ApplicationException} as its
 * direct subtypes. They have no source declaration, so they live here as compile-time singletons in
 * the {@link TypeEnvironment} rather than as {@link org.solvik.semantic.ClassSymbol}s; catch matching
 * is nominal over class names (built via the exception hierarchy graph), so a shared runtime subclass
 * chain is not required.
 *
 * <p>Because these types have no declaration, the name-keyed superclass graph built from source
 * declarations alone cannot see the edges between them. {@link #baseEdges()} supplies exactly those
 * edges so the graph is complete: without them a chain that passes <em>through</em> a built-in base is
 * truncated, and a handler written on the root {@code Exception} type stops catching classes that
 * derive from {@code RuntimeException}. This class is the single authority for the built-in hierarchy
 * so that no two copies of it can disagree.
 */
public final class ExceptionBases {

    private ExceptionBases() {
    }

    /** The built-in {@code Exception} root guest-exception type. */
    public static final ClassType EXCEPTION = new ClassType("Exception");

    /** The built-in {@code RuntimeException} type; a direct subtype of {@link #EXCEPTION}. */
    public static final ClassType RUNTIME_EXCEPTION = new ClassType("RuntimeException");

    /** The built-in {@code ApplicationException} type; a direct subtype of {@link #EXCEPTION}. */
    public static final ClassType APPLICATION_EXCEPTION = new ClassType("ApplicationException");

    static {
        EXCEPTION.resolveSuperType(AnyType.INSTANCE);
        RUNTIME_EXCEPTION.resolveSuperType(EXCEPTION);
        APPLICATION_EXCEPTION.resolveSuperType(EXCEPTION);
    }

    /** The names of the three built-in bases. */
    public static Set<String> baseNames() {
        return Set.of(EXCEPTION.name(), RUNTIME_EXCEPTION.name(), APPLICATION_EXCEPTION.name());
    }

    /** Whether {@code name} names one of the three built-in guest exception bases. */
    public static boolean isBase(String name) {
        return baseNames().contains(name);
    }

    /**
     * The declared-superclass edges among the built-in bases, as a name-to-name map in the same form
     * the graph built from source declarations uses. {@code Exception} is the guest exception root and
     * so has no entry: its supertype in the type graph is {@code Any}, which is not an exception type
     * and must not become part of the exception chain.
     */
    public static Map<String, String> baseEdges() {
        Map<String, String> edges = new LinkedHashMap<>();
        edges.put(RUNTIME_EXCEPTION.name(), EXCEPTION.name());
        edges.put(APPLICATION_EXCEPTION.name(), EXCEPTION.name());
        return edges;
    }
}
