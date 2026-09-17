/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.regex.RegexPattern;
import org.solvik.truffle.object.SolvikRegex;

/**
 * A {@code Regex(pattern)} construction whose pattern is a source constant
 * (docs/LANGUAGE_SPEC.md section 14). Static analysis compiled the pattern exactly once and lowering
 * built this node once, so every execution reuses the same compiled pattern and the same runtime
 * value rather than recompiling inside a loop.
 */
@NodeInfo(shortName = "Regex", description = "A constant Solvik Regex value")
public final class SolvikRegexLiteralNode extends SolvikExpressionNode {

    private final SolvikRegex value;

    public SolvikRegexLiteralNode(RegexPattern pattern) {
        this.value = new SolvikRegex(pattern.source(), pattern.compiled());
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return value;
    }
}
