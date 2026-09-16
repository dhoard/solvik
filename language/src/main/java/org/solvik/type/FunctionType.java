/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.type;

import java.util.List;
import java.util.Objects;

/**
 * The compile-time type of a declared function, used by the analyzer to type a call's callee.
 *
 * <p>Solvik functions are not first-class values: a bare function name is an error anywhere except
 * as the direct callee of a call, so a {@code FunctionType} is never a legal variable or parameter
 * type. It exists so a call expression can be typed completely during Phase 4, before method and
 * function values are specified.
 */
public final class FunctionType extends Type {

    private final List<Type> parameterTypes;
    private final Type returnType;

    public FunctionType(List<Type> parameterTypes, Type returnType) {
        super(render(parameterTypes, returnType));
        this.parameterTypes = List.copyOf(parameterTypes);
        this.returnType = Objects.requireNonNull(returnType);
    }

    public List<Type> parameterTypes() {
        return parameterTypes;
    }

    public Type returnType() {
        return returnType;
    }

    private static String render(List<Type> parameterTypes, Type returnType) {
        Objects.requireNonNull(parameterTypes);
        Objects.requireNonNull(returnType);
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < parameterTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parameterTypes.get(i).name());
        }
        return sb.append(") -> ").append(returnType.name()).toString();
    }
}
