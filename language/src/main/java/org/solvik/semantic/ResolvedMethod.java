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
package org.solvik.semantic;

import java.util.Objects;

/**
 * The static resolution of a method call: the target method, whether the receiver is the enclosing
 * instance's implicit {@code this} (unqualified call) rather than a written receiver expression, and
 * whether the call is a {@code super.member(...)} access that must bypass virtual dispatch and call
 * the immediate superclass implementation directly.
 *
 * <p>Every other call dispatches on the receiver's runtime class table by name, which is what makes
 * an interface default method body such as {@code greeting() { return "Hello " + name(); }} reach the
 * concrete implementor of the requirement rather than the interface.
 */
public final class ResolvedMethod {

    private final FunctionSymbol method;
    private final boolean implicitThis;
    private final boolean superCall;

    ResolvedMethod(FunctionSymbol method, boolean implicitThis) {
        this(method, implicitThis, false);
    }

    ResolvedMethod(FunctionSymbol method, boolean implicitThis, boolean superCall) {
        this.method = Objects.requireNonNull(method);
        this.implicitThis = implicitThis;
        this.superCall = superCall;
    }

    public FunctionSymbol method() {
        return method;
    }

    /** Whether the call has no written receiver and dispatches on the enclosing {@code this}. */
    public boolean isImplicitThis() {
        return implicitThis;
    }

    /** Whether the call statically targets the immediate superclass implementation. */
    public boolean isSuperCall() {
        return superCall;
    }
}
