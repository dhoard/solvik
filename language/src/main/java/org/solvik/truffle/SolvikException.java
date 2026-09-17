/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
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

    /** A Solvik runtime bounds error, raised by an out-of-range {@code List.get}. */
    @TruffleBoundary
    public static SolvikException boundsError(String message, Node location) {
        return new SolvikException("bounds error: " + message, location);
    }

    /** A Solvik runtime collection error, raised by a missing {@code Map} key or an empty {@code Stack}. */
    @TruffleBoundary
    public static SolvikException collectionError(String message, Node location) {
        return new SolvikException("collection error: " + message, location);
    }

    /**
     * A Solvik runtime type error for a call to a member no built-in collection exposes. The message
     * is built here rather than at the call site so the runtime-compiled collection dispatch carries
     * no string concatenation of its own.
     */
    @TruffleBoundary
    public static SolvikException unknownCollectionMember(String memberName, Node location) {
        return new SolvikException("type error: unknown member '" + memberName + "'", location);
    }

    /** A Solvik runtime regex error, raised by an invalid dynamically constructed pattern. */
    @TruffleBoundary
    public static SolvikException regexError(String message, Node location) {
        return new SolvikException("regex error: " + message, location);
    }

    /**
     * A Solvik internal invariant violation for a call whose supplied frame argument count does not
     * match the resolved callable. Source programs cannot trigger this: arity is validated during
     * semantic analysis, so reaching it means malformed internal call state rather than an invalid
     * program, and the message is deliberately distinguishable from a semantic arity error. The
     * message is built here so the runtime-compiled caller carries no string concatenation.
     */
    @TruffleBoundary
    public static SolvikException internalArity(String callable, int expected, int supplied, Node location) {
        return new SolvikException("internal error: callable '" + callable + "' expected " + expected + " frame argument(s) but execution supplied " + supplied, location);
    }
}
