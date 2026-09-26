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
     * A guest-visible failure for a thrown value that reaches the program boundary uncaught. A guest
     * throw travels as a control-flow signal (so it is catchable by an enclosing {@code try}); when it
     * escapes the outermost eval root with no handler left, that signal is converted here into an
     * {@link AbstractTruffleException} carrying the thrown class name, so the host reports it as an
     * ordinary guest failure rather than an internal error (docs/LANGUAGE_SPEC.md error-handling phases).
     * The message is built at the boundary so the propagation path itself allocates nothing.
     */
    @TruffleBoundary
    public static SolvikException uncaughtGuestThrow(Object value, Node location) {
        if (value instanceof org.solvik.truffle.object.SolvikAny any) {
            String message = exceptionMessage(any);
            String suffix = message == null ? "" : " with message '" + message + "'";
            return new SolvikException("uncaught guest exception of class '" + any.solvikClass().name() + "'" + suffix, location);
        }
        return new SolvikException("uncaught guest exception of class '" + String.valueOf(value) + "'", location);
    }

    /**
     * The synthesized message of a guest exception value, or {@code null} when it carries none. Read at
     * the program boundary only, so the object-store access sits behind a {@link TruffleBoundary} and the
     * hot throw/propagate path never touches it.
     */
    @TruffleBoundary
    private static String exceptionMessage(org.solvik.truffle.object.SolvikAny value) {
        Object key = value.solvikClass().messageKey();
        if (key == null) {
            return null;
        }
        Object message = com.oracle.truffle.api.object.DynamicObject.GetNode.getUncached().execute(value, key, null);
        // A Solvik String value is carried as a java.lang.String at run time (a string literal is a
        // plain String node), so the stored message is a String and not a TruffleString.
        return message instanceof String string ? string : null;
    }

    /**
     * A Solvik runtime type error raised by {@code Result.unwrap}/{@code Result.unwrapErr} on a value
     * of the wrong variant. This is a host runtime fault (an {@link AbstractTruffleException}), the same
     * class of failure as an arithmetic or cast error, and is reported to the host as an ordinary guest
     * failure (docs/LANGUAGE_SPEC.md error-handling operations). The message is built here so the
     * runtime-compiled operation node carries no string formatting of its own.
     */
    @TruffleBoundary
    public static SolvikException unwrapFailed(String operation, String presentVariant, Node location) {
        return new SolvikException("type error: " + operation + " called on a Result holding '" + presentVariant + "'", location);
    }

    /**
     * A Solvik runtime type error raised by {@code Result.expect(message)} on an {@code Err}, which
     * combines the caller-supplied message with the rendered carried error. The full message is built
     * here so the runtime-compiled operation node performs no string formatting.
     */
    @TruffleBoundary
    public static SolvikException expectFailed(String message, Object errorPayload, Node location) {
        return new SolvikException("expect failed: " + message + " (error: " + SolvikDisplay.render(errorPayload) + ")", location);
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
