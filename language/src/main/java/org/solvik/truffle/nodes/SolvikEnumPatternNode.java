/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.object.SolvikEnumValue;
import org.solvik.truffle.object.SolvikEnumVariant;

/**
 * An enum variant pattern (docs/LANGUAGE_SPEC.md section 12). The variant is fixed when the node is
 * created from the compiler's closed-variant metadata, so the test is a direct variant-identity
 * comparison followed by the nested argument patterns. Each argument pattern is matched against the
 * corresponding positional value of the enum value.
 */
@NodeInfo(shortName = "variant", description = "A Solvik enum variant pattern")
public final class SolvikEnumPatternNode extends SolvikPatternNode {

    private final SolvikEnumVariant variant;
    @Children private final SolvikPatternNode[] arguments;

    public SolvikEnumPatternNode(SolvikEnumVariant variant, SolvikPatternNode[] arguments) {
        this.variant = variant;
        this.arguments = arguments;
    }

    @Override
    public boolean matches(VirtualFrame frame, Object value) {
        if (!(value instanceof SolvikEnumValue enumValue) || enumValue.variant() != variant) {
            return false;
        }
        for (int i = 0; i < arguments.length; i++) {
            if (!arguments[i].matches(frame, enumValue.value(i))) {
                return false;
            }
        }
        return true;
    }
}
