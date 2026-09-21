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

import java.util.ArrayList;
import java.util.Objects;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import org.solvik.truffle.SolvikException;
import org.solvik.truffle.SolvikUnit;

/**
 * The runtime representation of the mutable built-in {@code List<T>}. Elements are erased to
 * {@code Object} and stored in an {@code ArrayList}; the erased runtime cannot enforce the invariant
 * element type, so the compiler checks it statically. Indices are zero-based; an out-of-range index
 * raises a Solvik bounds error anchored at the call node.
 */
public final class SolvikList extends SolvikBuiltinCollection {

    private final ArrayList<Object> elements = new ArrayList<>();

    public SolvikList() {
        super("List");
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public Object invoke(String memberName, Object[] arguments, Node location) {
        switch (memberName) {
            case "add":
                if (arguments.length != 1) {
                    throw SolvikException.arithmetic("add expects one argument", location);
                }
                elements.add(arguments[0]);
                return SolvikUnit.INSTANCE;
            case "get":
                if (arguments.length != 1) {
                    throw SolvikException.arithmetic("get expects one argument", location);
                }
                int getIndex = intValue(arguments[0], location);
                if (getIndex < 0 || getIndex >= elements.size()) {
                    throw boundsFailure(getIndex, location);
                }
                return elements.get(getIndex);
            case "set":
                if (arguments.length != 2) {
                    throw SolvikException.arithmetic("set expects two arguments", location);
                }
                int setIndex = intValue(arguments[0], location);
                if (setIndex < 0 || setIndex >= elements.size()) {
                    throw boundsFailure(setIndex, location);
                }
                elements.set(setIndex, arguments[1]);
                return SolvikUnit.INSTANCE;
            case "removeAt":
                if (arguments.length != 1) {
                    throw SolvikException.arithmetic("removeAt expects one argument", location);
                }
                int removeIndex = intValue(arguments[0], location);
                if (removeIndex < 0 || removeIndex >= elements.size()) {
                    throw boundsFailure(removeIndex, location);
                }
                return elements.remove(removeIndex);
            case "clear":
                elements.clear();
                return SolvikUnit.INSTANCE;
            case "size":
                return elements.size();
            case "isEmpty":
                return elements.isEmpty();
            default:
                throw SolvikException.unknownCollectionMember(memberName, location);
        }
    }

    /**
     * Builds the out-of-range failure outside compiled code, so the message construction is not
     * reachable for Truffle runtime compilation (docs/ARCHITECTURE.md native-image concerns).
     */
    @TruffleBoundary
    private SolvikException boundsFailure(int index, Node location) {
        return SolvikException.boundsError("index " + index + " is out of range for list of size " + elements.size(), location);
    }

    /**
     * Pre-populates a list from erased elements. Internal: every runtime producer (for example
     * {@link SolvikRegex}) seeds a fresh instance so one result never aliases another, and a source
     * {@code List(elements...)} construction passes its initial elements here.
     */
    public SolvikList(Object[] initialElements) {
        this();
        for (Object element : initialElements) {
            this.elements.add(element);
        }
    }

    /** Reads the boxed integer value of an erased index argument, anchored at the call node. */
    private static int intValue(Object argument, Node location) {
        if (argument instanceof Integer i) {
            return i.intValue();
        }
        if (argument instanceof Long l) {
            return l.intValue();
        }
        throw SolvikException.arithmetic("expected Integer index", location);
    }
}
