/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikException;

/**
 * An explicit numeric conversion {@code T(value)} (docs/LANGUAGE_SPEC.md section 4). There is no
 * implicit widening or narrowing: every conversion is written. An integral target rejects an
 * out-of-range value with a Solvik runtime arithmetic error; floating-point targets follow IEEE
 * 754 and do not range-check.
 *
 * <p>Numeric operands are dispatched through concrete {@code instanceof} branches rather than
 * {@code Number.xxxValue()} so runtime compilation never reaches the blocklisted boxed-number
 * implementations of arbitrary subclasses.
 */
@NodeInfo(shortName = "convert", description = "An explicit Solvik numeric conversion")
public final class SolvikConvertNode extends SolvikExpressionNode {

    public enum Target {
        BYTE,
        SHORT,
        INT,
        LONG,
        FLOAT,
        DOUBLE
    }

    private final Target target;
    @Child private SolvikExpressionNode operand;

    public SolvikConvertNode(Target target, SolvikExpressionNode operand) {
        this.target = target;
        this.operand = operand;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object value = operand.executeGeneric(frame);
        return switch (target) {
            case BYTE -> (byte) checkRange(integralValue(value), Byte.MIN_VALUE, Byte.MAX_VALUE, "Byte");
            case SHORT -> (short) checkRange(integralValue(value), Short.MIN_VALUE, Short.MAX_VALUE, "Short");
            case INT -> (int) checkRange(integralValue(value), Integer.MIN_VALUE, Integer.MAX_VALUE, "Int");
            case LONG -> integralValue(value);
            case FLOAT -> (float) floatingValue(value);
            case DOUBLE -> floatingValue(value);
        };
    }

    /** Retrieves the integral value of any numeric operand, rejecting a non-finite floating source. */
    private long integralValue(Object value) {
        if (value instanceof Byte b) {
            return b.byteValue();
        }
        if (value instanceof Short s) {
            return s.shortValue();
        }
        if (value instanceof Integer i) {
            return i.intValue();
        }
        if (value instanceof Long l) {
            return l.longValue();
        }
        if (value instanceof Float f) {
            return floatingToIntegral(f.floatValue());
        }
        if (value instanceof Double d) {
            return floatingToIntegral(d.doubleValue());
        }
        throw new IllegalStateException("invalid numeric conversion operand");
    }

    private long floatingToIntegral(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < Long.MIN_VALUE || value > Long.MAX_VALUE) {
            throw outOfRange("an integral type");
        }
        return (long) value;
    }

    private double floatingValue(Object value) {
        if (value instanceof Byte b) {
            return b.byteValue();
        }
        if (value instanceof Short s) {
            return s.shortValue();
        }
        if (value instanceof Integer i) {
            return i.intValue();
        }
        if (value instanceof Long l) {
            return l.longValue();
        }
        if (value instanceof Float f) {
            return f.floatValue();
        }
        if (value instanceof Double d) {
            return d.doubleValue();
        }
        throw new IllegalStateException("invalid numeric conversion operand");
    }

    private long checkRange(long value, long min, long max, String name) {
        if (value < min || value > max) {
            throw outOfRange(name);
        }
        return value;
    }

    @TruffleBoundary
    private SolvikException outOfRange(String name) {
        return SolvikException.arithmetic("conversion to " + name + " is out of range", this);
    }
}
