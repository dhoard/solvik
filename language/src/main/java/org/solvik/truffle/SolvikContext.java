/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026-present Douglas Hoard
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.truffle;

import java.io.PrintWriter;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.Node;

/**
 * The run-time state of a Solvik execution: the Truffle environment and the writer used by the
 * predeclared {@code print}/{@code println} functions. The Phase 5 core keeps no global symbol
 * table because every call is resolved statically before lowering.
 */
public final class SolvikContext {

    private static final ContextReference<SolvikContext> REFERENCE = ContextReference.create(SolvikLanguage.class);

    private final SolvikLanguage language;
    @CompilationFinal private Env env;
    private final PrintWriter output;

    public SolvikContext(SolvikLanguage language, Env env) {
        this.language = language;
        this.env = env;
        this.output = new PrintWriter(env.out(), true);
    }

    public SolvikLanguage getLanguage() {
        return language;
    }

    public Env getEnv() {
        return env;
    }

    /** The destination of {@code print} and {@code println}. */
    public PrintWriter getOutput() {
        return output;
    }

    /** Writes a Solvik value with no trailing newline. */
    @TruffleBoundary
    public void print(Object value) {
        output.print(SolvikDisplay.render(value));
        output.flush();
    }

    /** Writes a Solvik value followed by the platform line separator. */
    @TruffleBoundary
    public void println(Object value) {
        output.println(SolvikDisplay.render(value));
    }

    /** Rebinds the environment during native-image context patching. */
    public void patchContext(Env newEnv) {
        this.env = newEnv;
    }

    public static SolvikContext get(Node node) {
        return REFERENCE.get(node);
    }
}
