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

/**
 * The three predeclared guest exception base types (docs/LANGUAGE_SPEC.md error-handling phases):
 * {@code Exception} at the root, with {@code RuntimeException} and {@code ApplicationException} as its
 * direct subtypes. They have no source declaration, so they live here as compile-time singletons in
 * the {@link TypeEnvironment} rather than as {@link org.solvik.semantic.ClassSymbol}s; catch matching
 * is nominal over class names (built via the exception hierarchy graph), so a shared runtime subclass
 * chain is not required.
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
}
