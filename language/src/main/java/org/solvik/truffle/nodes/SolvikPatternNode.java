/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * Base class of the runtime pattern matchers used by a lowered {@code match}
 * (docs/LANGUAGE_SPEC.md section 12). A pattern node tests one already-evaluated value and, when it
 * matches, writes the names it binds into frame slots so the selected branch's result expression can
 * read them. Patterns are not value-producing expressions, so this node extends {@link Node} rather
 * than {@link SolvikExpressionNode}.
 */
@NodeInfo(description = "A Solvik match pattern matcher")
public abstract class SolvikPatternNode extends Node {

    /** Whether {@code value} matches this pattern, binding names into {@code frame} when it does. */
    public abstract boolean matches(VirtualFrame frame, Object value);
}
