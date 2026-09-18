/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.object;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.object.DynamicObject;

/**
 * A Solvik user-class instance (docs/ARCHITECTURE.md "Objects and Truffle Shape"): a runtime class
 * reference plus shape-backed instance storage. Only the properties declared on the class exist;
 * undeclared member access is rejected statically and there is no runtime API that inserts members.
 *
 * <p>{@code SolvikAny} is the runtime representation a user-defined class instance uses; it is not
 * the language's root type {@code Any}, which is represented by the compiler {@code AnyType} and has
 * no dedicated runtime carrier.
 */
@ExportLibrary(InteropLibrary.class)
@SuppressWarnings("serial")
public final class SolvikAny extends DynamicObject implements TruffleObject {

    private final SolvikClass solvikClass;

    public SolvikAny(SolvikClass solvikClass) {
        super(SolvikClass.rootShape());
        this.solvikClass = solvikClass;
    }

    public SolvikClass solvikClass() {
        return solvikClass;
    }

    @ExportMessage
    @TruffleBoundary
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return solvikClass.name();
    }
}
