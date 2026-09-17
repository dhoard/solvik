/*
 * Copyright (c) 2026-present Douglas Hoard
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
