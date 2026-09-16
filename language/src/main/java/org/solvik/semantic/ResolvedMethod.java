/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.semantic;

import java.util.Objects;

/**
 * The static resolution of a method call: the target method, whether the receiver is the enclosing
 * instance's implicit {@code this} (unqualified call) rather than a written receiver expression, and
 * whether the call is a {@code super.member(...)} access that must bypass virtual dispatch and call
 * the immediate superclass implementation directly.
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
