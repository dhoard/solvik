/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * The call target returned by {@link SolvikLanguage#parse}. Evaluating a Solvik source file runs its
 * validated implicit {@code main} (the file's executable top-level statements); a program with no
 * top-level statements remains valid and does nothing (docs/LANGUAGE_SPEC.md section 6).
 *
 * <p>Class initializers are not run here. Each class initializer is installed on its own runtime class
 * and runs lazily on that class's first active use (docs/LANGUAGE_SPEC.md section 7).
 */
public final class SolvikEvalRootNode extends RootNode {

    private final SolvikFunction entryPoint;

    public SolvikEvalRootNode(SolvikLanguage language, SolvikFunction entryPoint) {
        super(language);
        this.entryPoint = entryPoint;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (entryPoint != null) {
            entryPoint.callTarget().call(new Object[0]);
        }
        return SolvikUnit.INSTANCE;
    }

    @Override
    public String getName() {
        return "<eval>";
    }
}
