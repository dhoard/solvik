/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;

/**
 * A Solvik runtime error such as integer overflow or division by zero. These are the only
 * conditions the statically checked Phase 5 core can fail on at run time; every type error is
 * rejected before lowering.
 */
@SuppressWarnings("serial")
public final class SolvikException extends AbstractTruffleException {

    private SolvikException(String message, Node location) {
        super(message, location);
    }

    /** A Solvik runtime arithmetic error (overflow, division by zero). */
    @TruffleBoundary
    public static SolvikException arithmetic(String message, Node location) {
        return new SolvikException("arithmetic error: " + message, location);
    }

    /** A Solvik runtime type error, raised by an unsuccessful {@code as} cast. */
    @TruffleBoundary
    public static SolvikException typeError(String message, Node location) {
        return new SolvikException("type error: " + message, location);
    }
}
