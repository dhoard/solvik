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
package org.solvik.truffle.object;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;

/**
 * The common erased runtime receiver for a mutable built-in collection ({@code List}, {@code Set},
 * {@code Map}, or {@code Stack}). Each concrete collection extends this abstract receiver with its
 * own storage and a single {@link #invoke} that dispatches a member call to the implementation.
 *
 * <p>The erased runtime knows nothing about the erased generic type arguments, so it cannot
 * validate them; the compiler already checked arguments statically and lowering passes only the
 * source arguments. A member call executes {@link #invoke(memberName, arguments, location)}, where
 * {@code arguments} carries the source arguments only (never the receiver), {@code location}
 * anchors any guest runtime error, and every {@code Unit}-returning mutator yields
 * {@link SolvikUnit#INSTANCE}.
 */
public abstract class SolvikBuiltinCollection {

    protected SolvikBuiltinCollection(String typeName) {
        this.typeName = typeName;
    }

    private final String typeName;

    public String typeName() {
        return typeName;
    }

    /** The number of elements, exposed as the read-only {@code val size: Integer}. */
    public abstract int size();

    /**
     * Executes a member call on the erased receiver. The arguments carry only the source arguments;
     * every argument is evaluated exactly once before invocation, and a {@code Unit}-returning mutator
     * returns {@link SolvikUnit#INSTANCE}.
     */
    @TruffleBoundary
    public abstract Object invoke(String memberName, Object[] arguments, Node location);
}
