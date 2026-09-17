/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle;

import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import org.solvik.truffle.nodes.SolvikReturnException;
import org.solvik.truffle.nodes.SolvikStatementNode;

/**
 * The root of a lowered Solvik function. Parameters arrive as an {@code Object[]} through the
 * Truffle frame arguments and are copied into typed frame slots before the body runs, so a
 * statically typed {@code Int} or {@code Boolean} parameter is used without boxing inside the body.
 *
 * <p>The frame argument count is checked defensively against the resolved parameter slots. Source
 * arity is validated during semantic analysis, so a mismatch here is an internal invariant violation
 * rather than a source-level arity error (docs/ARCHITECTURE.md, docs/LANGUAGE_SPEC.md section 6).
 */
public final class SolvikRootNode extends RootNode {

    private final String name;
    private final boolean returnsValue;
    private final int[] parameterSlots;
    private final FrameSlotKind[] parameterKinds;
    private final Source source;
    private final int sourceStart;
    private final int sourceLength;
    @Child private SolvikStatementNode body;

    public SolvikRootNode(SolvikLanguage language, FrameDescriptor frameDescriptor, SolvikStatementNode body, String name, boolean returnsValue, int[] parameterSlots, FrameSlotKind[] parameterKinds,
                    Source source, int sourceStart, int sourceLength) {
        super(language, frameDescriptor);
        this.body = body;
        this.name = name;
        this.returnsValue = returnsValue;
        this.parameterSlots = parameterSlots;
        this.parameterKinds = parameterKinds;
        this.source = source;
        this.sourceStart = sourceStart;
        this.sourceLength = sourceLength;
    }

    @Override
    public SourceSection getSourceSection() {
        if (source == null) {
            return null;
        }
        int start = Math.min(sourceStart, source.getLength());
        int length = Math.min(sourceLength, source.getLength() - start);
        return source.createSection(start, length);
    }

    @Override
    public Object execute(VirtualFrame frame) {
        copyArguments(frame);
        try {
            body.executeVoid(frame);
        } catch (SolvikReturnException e) {
            return e.value();
        }
        if (returnsValue) {
            throw new IllegalStateException("value-returning Solvik function '" + name + "' fell through without returning");
        }
        return SolvikUnit.INSTANCE;
    }

    private void copyArguments(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();
        if (arguments.length != parameterSlots.length) {
            // Defensive invariant: source-level arity is validated during semantic analysis, so a
            // mismatch here means malformed internal call state rather than an invalid program.
            throw SolvikException.internalArity(name, parameterSlots.length, arguments.length, this);
        }
        for (int i = 0; i < parameterSlots.length; i++) {
            Object value = arguments[i];
            int slot = parameterSlots[i];
            FrameSlotKind kind = parameterKinds[i];
            switch (kind) {
                case Int -> frame.setInt(slot, (Integer) value);
                case Boolean -> frame.setBoolean(slot, (Boolean) value);
                case Long -> frame.setLong(slot, (Long) value);
                case Float -> frame.setFloat(slot, (Float) value);
                case Double -> frame.setDouble(slot, (Double) value);
                default -> frame.setObject(slot, value);
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }
}
