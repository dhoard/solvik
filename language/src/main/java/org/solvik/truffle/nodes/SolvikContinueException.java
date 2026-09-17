/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.nodes.ControlFlowException;

/** Control-flow signal thrown by {@code continue} and caught by the innermost enclosing loop. */
@SuppressWarnings("serial")
public final class SolvikContinueException extends ControlFlowException {

    public static final SolvikContinueException INSTANCE = new SolvikContinueException();

    private SolvikContinueException() {
    }
}
