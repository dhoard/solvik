/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * The wildcard pattern {@code _} (docs/LANGUAGE_SPEC.md section 12). It matches every value and
 * binds nothing, so its runtime cost is a constant true.
 */
@NodeInfo(shortName = "_", description = "A Solvik wildcard pattern")
public final class SolvikWildcardPatternNode extends SolvikPatternNode {

    @Override
    public boolean matches(VirtualFrame frame, Object value) {
        return true;
    }
}
