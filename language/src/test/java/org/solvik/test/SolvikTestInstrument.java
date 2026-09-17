/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 */
package org.solvik.test;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;

/**
 * A test-only Truffle instrument used by {@link SolvikInstrumentationTest} to expose the engine's
 * {@link TruffleInstrument.Env} and therefore its {@code Instrumenter}. It is discovered through the
 * generated {@code TruffleInstrumentProvider} declared by the test module.
 */
@Registration(id = SolvikTestInstrument.ID, name = "Solvik Test Instrument", version = "1.0", services = SolvikTestInstrument.class)
public final class SolvikTestInstrument extends TruffleInstrument {

    public static final String ID = "solvik-test-instrument";

    private Env env;

    @Override
    protected void onCreate(Env env) {
        this.env = env;
        env.registerService(this);
    }

    public Env env() {
        return env;
    }
}
