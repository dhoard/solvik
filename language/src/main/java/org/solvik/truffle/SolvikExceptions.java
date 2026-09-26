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
package org.solvik.truffle;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.solvik.truffle.object.SolvikClass;

/**
 * The runtime representation of Solvik's built-in guest exception hierarchy
 * (docs/LANGUAGE_SPEC.md error-handling phases). The three base types — {@code Exception},
 * {@code RuntimeException}, and {@code ApplicationException} — are declared by the language, not by
 * source, so they have no {@link org.solvik.semantic.ClassSymbol}; instead they live here as stable
 * {@link SolvikClass} identity. A user-declared exception class that extends one of them inherits its
 * runtime superclass through this registry, which is what makes nominal catch matching work across the
 * whole program: a handler written for {@code RuntimeException} matches any instance whose runtime
 * superclass chain reaches this shared {@code RuntimeException}.
 */
public final class SolvikExceptions {

    /** Built-in exception type names recognized by throw and catch. */
    public static final String EXCEPTION = "Exception";
    public static final String RUNTIME_EXCEPTION = "RuntimeException";
    public static final String APPLICATION_EXCEPTION = "ApplicationException";

    private static final Map<String, SolvikClass> REGISTRY = new ConcurrentHashMap<>();

    private SolvikExceptions() {
    }

    /** The shared {@code Exception} base class. It derives directly from {@code Any}. */
    public static SolvikClass exception() {
        return REGISTRY.computeIfAbsent(EXCEPTION, name -> new SolvikClass(name, List.of(), List.of()));
    }

    /** The shared {@code RuntimeException} base class; it extends the built-in {@link #exception()}. */
    public static SolvikClass runtimeException() {
        return REGISTRY.computeIfAbsent(RUNTIME_EXCEPTION, name -> withSuperclass(
                new SolvikClass(name, List.of(), List.of()), exception()));
    }

    /** The shared {@code ApplicationException} base class; it extends the built-in {@link #exception()}. */
    public static SolvikClass applicationException() {
        return REGISTRY.computeIfAbsent(APPLICATION_EXCEPTION, name -> withSuperclass(
                new SolvikClass(name, List.of(), List.of()), exception()));
    }

    private static SolvikClass withSuperclass(SolvikClass declared, SolvikClass sup) {
        declared.setSuperClass(sup);
        return declared;
    }

    /** Whether {@code name} is one of the built-in exception base types. */
    public static boolean isBaseType(String name) {
        return EXCEPTION.equals(name) || RUNTIME_EXCEPTION.equals(name) || APPLICATION_EXCEPTION.equals(name);
    }
}
