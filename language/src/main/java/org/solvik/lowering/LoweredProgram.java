/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.lowering;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import com.oracle.truffle.api.CallTarget;
import org.solvik.semantic.CheckedProgram;
import org.solvik.truffle.SolvikFunction;

/**
 * The result of lowering a {@link CheckedProgram} to the Truffle AST backend: one
 * {@link SolvikFunction} per declared source function plus the call target that evaluates the
 * source file's {@code main}. This is the only runtime representation produced from the typed
 * program; no executable node exists for a program that failed static analysis.
 */
public final class LoweredProgram {

    private final CheckedProgram program;
    private final Map<String, SolvikFunction> functions;
    private final CallTarget evalTarget;

    LoweredProgram(CheckedProgram program, Map<String, SolvikFunction> functions, CallTarget evalTarget) {
        this.program = Objects.requireNonNull(program);
        this.functions = Collections.unmodifiableMap(new LinkedHashMap<>(functions));
        this.evalTarget = Objects.requireNonNull(evalTarget);
    }

    public CheckedProgram program() {
        return program;
    }

    /** The lowered functions in declaration order, keyed by name. */
    public Map<String, SolvikFunction> functions() {
        return functions;
    }

    public Optional<SolvikFunction> function(String name) {
        return Optional.ofNullable(functions.get(name));
    }

    /** The call target that runs {@code main} when the source is evaluated. */
    public CallTarget evalTarget() {
        return evalTarget;
    }
}
