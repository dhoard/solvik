/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.object;

import java.util.Objects;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import org.solvik.truffle.SolvikException;

/**
 * The runtime representation of the built-in immutable {@code List<T>} (docs/LANGUAGE_SPEC.md
 * section 11). It stores its elements in a fixed array and exposes {@code size} and a bounds-checked
 * {@code get}. Collection construction is deferred, so no source form currently creates one; the
 * type's static members are nevertheless executable should a construction form be specified later.
 */
public final class SolvikList {

    private final Object[] elements;

    public SolvikList(Object[] elements) {
        this.elements = Objects.requireNonNull(elements).clone();
    }

    /** The number of elements, exposed as {@code val size: Int}. */
    public int size() {
        return elements.length;
    }

    /** The element at {@code index}, raising a Solvik runtime bounds error when out of range. */
    public Object get(int index, Node location) {
        if (index < 0 || index >= elements.length) {
            throw boundsFailure(index, location);
        }
        return elements[index];
    }

    /**
     * Builds the out-of-range failure outside compiled code, so the message construction is not
     * reachable for Truffle runtime compilation (docs/ARCHITECTURE.md native-image concerns).
     */
    @TruffleBoundary
    private SolvikException boundsFailure(int index, Node location) {
        return SolvikException.boundsError("index " + index + " is out of range for list of size " + elements.length, location);
    }
}
