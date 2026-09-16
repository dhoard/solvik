/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import org.solvik.truffle.SolvikTypes;
import org.solvik.truffle.SolvikTypesGen;

/**
 * Base class for Solvik expressions. The {@link TypeSystemReference} gives every subclass the Truffle
 * DSL primitive fast paths declared by {@link SolvikTypes}, so {@code Int} and {@code Boolean}
 * values flow through the generated specializations without boxing.
 */
@TypeSystemReference(SolvikTypes.class)
@NodeInfo(description = "The abstract base node for all Solvik expressions")
public abstract class SolvikExpressionNode extends SolvikStatementNode {

    /** Evaluates this expression, yielding an {@code int}, {@code boolean}, {@code String}, or other object. */
    public abstract Object executeGeneric(VirtualFrame frame);

    @Override
    public void executeVoid(VirtualFrame frame) {
        executeGeneric(frame);
    }

    /**
     * Evaluates this expression as an {@code Int}. Static analysis guarantees the type, so a wrong
     * runtime value indicates an internal inconsistency rather than a user error.
     */
    public int executeInt(VirtualFrame frame) {
        try {
            return SolvikTypesGen.expectInteger(executeGeneric(frame));
        } catch (UnexpectedResultException e) {
            throw new IllegalStateException("expected an Int value", e);
        }
    }

    /** Evaluates this expression as a {@code Boolean}, which static analysis guarantees. */
    public boolean executeBoolean(VirtualFrame frame) {
        try {
            return SolvikTypesGen.expectBoolean(executeGeneric(frame));
        } catch (UnexpectedResultException e) {
            throw new IllegalStateException("expected a Boolean value", e);
        }
    }

    /** Evaluates this expression as a {@code Long}, which static analysis guarantees. */
    public long executeLong(VirtualFrame frame) {
        try {
            return SolvikTypesGen.expectLong(executeGeneric(frame));
        } catch (UnexpectedResultException e) {
            throw new IllegalStateException("expected a Long value", e);
        }
    }

    /** Evaluates this expression as a {@code Float}, which static analysis guarantees. */
    public float executeFloat(VirtualFrame frame) {
        try {
            return SolvikTypesGen.expectFloat(executeGeneric(frame));
        } catch (UnexpectedResultException e) {
            throw new IllegalStateException("expected a Float value", e);
        }
    }

    /** Evaluates this expression as a {@code Double}, which static analysis guarantees. */
    public double executeDouble(VirtualFrame frame) {
        try {
            return SolvikTypesGen.expectDouble(executeGeneric(frame));
        } catch (UnexpectedResultException e) {
            throw new IllegalStateException("expected a Double value", e);
        }
    }
}
