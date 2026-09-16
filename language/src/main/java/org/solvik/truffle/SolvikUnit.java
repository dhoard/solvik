/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/**
 * The single runtime value of the Solvik {@code Unit} type (docs/LANGUAGE_SPEC.md section 4). A
 * function that returns normally without a value produces this singleton. It is visible to interop
 * as a null-like value so evaluating a unit-returning {@code main} produces no result.
 */
@ExportLibrary(InteropLibrary.class)
public final class SolvikUnit implements TruffleObject {

    /** The one and only {@code Unit} value. */
    public static final SolvikUnit INSTANCE = new SolvikUnit();

    private SolvikUnit() {
    }

    @ExportMessage
    boolean isNull() {
        return true;
    }

    @ExportMessage
    Object toDisplayString(@SuppressWarnings("unused") boolean allowSideEffects) {
        return "Unit";
    }

    @Override
    public String toString() {
        return "Unit";
    }
}
