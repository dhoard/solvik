/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

/** The {@code null} literal (docs/LANGUAGE_SPEC.md section 5); its value is the runtime null. */
@NodeInfo(shortName = "null", description = "The Solvik null literal")
public final class SolvikNullLiteralNode extends SolvikExpressionNode {

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return null;
    }
}
