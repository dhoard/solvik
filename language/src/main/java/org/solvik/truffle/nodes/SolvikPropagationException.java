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
package org.solvik.truffle.nodes;

import com.oracle.truffle.api.nodes.ControlFlowException;

/**
 * Control-flow signal that unwinds a {@code Result}-returning Solvik function body when a postfix
 * propagation ({@code expression?}) observes an {@code Err} (docs/LANGUAGE_SPEC.md, error-handling
 * phases). It carries the underlying {@code Err} value, which the enclosing function returns as its
 * own result so propagation composes across nested calls without a second execution backend. It is a
 * {@link ControlFlowException}, exactly like {@link SolvikReturnException}, so Truffle treats it as
 * ordinary control flow rather than an error condition during profiling or compilation.
 */
@SuppressWarnings("serial")
public final class SolvikPropagationException extends ControlFlowException {

    private final Object value;

    public SolvikPropagationException(Object value) {
        // Control flow exceptions unwind to the enclosing Result-returning boundary; the carried
        // value is the Err, which that function returns as its own result. No message is needed: the
        // exception type and value together distinguish propagation from a runtime error.
        this.value = value;
    }

    /** The carried {@code Err} value, which the enclosing function returns as its result. */
    public Object value() {
        return value;
    }
}
