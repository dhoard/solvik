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
import org.solvik.truffle.object.SolvikEnumVariant;
import org.solvik.truffle.object.SolvikEnumValue;

/**
 * Constructs a Solvik enum value from a statically resolved variant (docs/LANGUAGE_SPEC.md
 * section 12). The variant's identity is fixed when the node is created, so no runtime lookup is
 * needed; the node evaluates its positional values and yields an immutable enum value.
 */
@NodeInfo(shortName = "new-enum", description = "Construct a Solvik enum variant value")
public final class SolvikEnumConstructNode extends SolvikExpressionNode {

    private final SolvikEnumVariant variant;
    @Children private final SolvikExpressionNode[] values;

    public SolvikEnumConstructNode(SolvikEnumVariant variant, SolvikExpressionNode[] values) {
        this.variant = variant;
        this.values = values;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object[] evaluated = new Object[values.length];
        for (int i = 0; i < evaluated.length; i++) {
            evaluated[i] = values[i].executeGeneric(frame);
        }
        return new SolvikEnumValue(variant, evaluated);
    }
}
