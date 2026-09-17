/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikRuntimeTypes;
import org.solvik.type.Type;

/**
 * A binding pattern (docs/LANGUAGE_SPEC.md section 12). A {@code name: Type} pattern first tests the
 * runtime type and then binds the value; a bare variant binding has no target type and binds
 * unconditionally. The binding's frame slot is fixed by lowering and always stores its value as an
 * object, because the bound value comes from a destructured enum value or a subtype test.
 */
@NodeInfo(shortName = "bind", description = "A Solvik binding pattern")
public final class SolvikBindingPatternNode extends SolvikPatternNode {

    private final int slot;
    /** The tested subtype, or {@code null} for a bare binding that matches every value. */
    private final Type target;
    private final SolvikClass targetClass;

    public SolvikBindingPatternNode(int slot, Type target, SolvikClass targetClass) {
        this.slot = slot;
        this.target = target;
        this.targetClass = targetClass;
    }

    @Override
    public boolean matches(VirtualFrame frame, Object value) {
        if (target != null && !SolvikRuntimeTypes.isInstance(value, target, targetClass)) {
            return false;
        }
        frame.setObject(slot, value);
        return true;
    }
}
