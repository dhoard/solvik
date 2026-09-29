/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle;

import java.util.Objects;
import com.oracle.truffle.api.RootCallTarget;
import org.solvik.truffle.object.SolvikFunctionValue;

/**
 * The runtime handle of a declared Solvik function: a name, the {@link RootCallTarget} produced by
 * typed lowering, and the one guest function value that names it. The handle exists whether or not
 * guest code ever treats the function as a value, so a program that only calls its functions by name
 * allocates a function value for none of them.
 *
 * <p>Lowering creates one {@code SolvikFunction} per declared function before lowering any body, so
 * mutually recursive and forward-referencing calls resolve. The call target is installed once the
 * body has been lowered.
 */
public final class SolvikFunction {

    private final String name;
    private RootCallTarget callTarget;
    /**
     * The one guest function value for this declaration, created on first demand. A reference to a
     * declared function is a name and not an allocation, so every reference must yield this same
     * value (docs/LANGUAGE_SPEC.md section 6); canonical identity follows from there being exactly one
     * handle per declaration.
     */
    private SolvikFunctionValue value;

    public SolvikFunction(String name) {
        this.name = Objects.requireNonNull(name);
    }

    public String name() {
        return name;
    }

    public RootCallTarget callTarget() {
        if (callTarget == null) {
            throw new IllegalStateException("function '" + name + "' was called before its body was lowered");
        }
        return callTarget;
    }

    /** Installs the lowered body; called exactly once by lowering. */
    public void install(RootCallTarget target) {
        if (callTarget != null) {
            throw new IllegalStateException("function '" + name + "' already has a call target");
        }
        this.callTarget = Objects.requireNonNull(target);
    }

    /**
     * The canonical function value for this declaration.
     *
     * <p>Lowering calls this once, while it builds the constant node that carries the value into the
     * tree, so the memo below has no concurrent writer: a function value is produced by lowering and
     * never by execution. Execution reaches the value only through that node or through a binding that
     * stored it.
     */
    public SolvikFunctionValue functionValue() {
        if (value == null) {
            value = SolvikFunctionValue.forFunction(this);
        }
        return value;
    }

    @Override
    public String toString() {
        return name;
    }
}
