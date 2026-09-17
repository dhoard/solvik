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

/**
 * The runtime handle of a declared Solvik function: a name plus the {@link RootCallTarget} produced
 * by typed lowering. Solvik functions are not first-class values (docs/LANGUAGE_SPEC.md section 3),
 * so this type is an internal execution detail and is never exposed to guest code as a value.
 *
 * <p>Lowering creates one {@code SolvikFunction} per declared function before lowering any body, so
 * mutually recursive and forward-referencing calls resolve. The call target is installed once the
 * body has been lowered.
 */
public final class SolvikFunction {

    private final String name;
    private RootCallTarget callTarget;

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

    @Override
    public String toString() {
        return name;
    }
}
