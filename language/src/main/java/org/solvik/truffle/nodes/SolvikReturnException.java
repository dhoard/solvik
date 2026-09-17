/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.nodes.ControlFlowException;

/** Control-flow signal used to unwind a Solvik function body. Carries the returned value. */
@SuppressWarnings("serial")
public final class SolvikReturnException extends ControlFlowException {

    private final Object value;

    public SolvikReturnException(Object value) {
        this.value = value;
    }

    public Object value() {
        return value;
    }
}
