/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.nodes.ControlFlowException;

/** Control-flow signal thrown by {@code break} and caught by the innermost enclosing loop. */
@SuppressWarnings("serial")
public final class SolvikBreakException extends ControlFlowException {

    public static final SolvikBreakException INSTANCE = new SolvikBreakException();

    private SolvikBreakException() {
    }
}
