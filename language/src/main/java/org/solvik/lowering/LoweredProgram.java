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
