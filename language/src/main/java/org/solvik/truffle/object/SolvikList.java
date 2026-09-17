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

import java.util.Objects;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import org.solvik.truffle.SolvikException;

/**
 * The runtime representation of the built-in immutable {@code List<T>} (docs/LANGUAGE_SPEC.md
 * section 11). It stores its elements in a fixed array and exposes {@code size} and a bounds-checked
 * {@code get}. Collection construction is deferred, so no source form currently creates one; the
 * type's static members are nevertheless executable should a construction form be specified later.
 */
public final class SolvikList {

    private final Object[] elements;

    public SolvikList(Object[] elements) {
        this.elements = Objects.requireNonNull(elements).clone();
    }

    /** The number of elements, exposed as {@code val size: Int}. */
    public int size() {
        return elements.length;
    }

    /** The element at {@code index}, raising a Solvik runtime bounds error when out of range. */
    public Object get(int index, Node location) {
        if (index < 0 || index >= elements.length) {
            throw boundsFailure(index, location);
        }
        return elements[index];
    }

    /**
     * Builds the out-of-range failure outside compiled code, so the message construction is not
     * reachable for Truffle runtime compilation (docs/ARCHITECTURE.md native-image concerns).
     */
    @TruffleBoundary
    private SolvikException boundsFailure(int index, Node location) {
        return SolvikException.boundsError("index " + index + " is out of range for list of size " + elements.length, location);
    }
}
