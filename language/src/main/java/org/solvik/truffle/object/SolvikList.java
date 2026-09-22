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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import org.solvik.truffle.SolvikException;
import org.solvik.truffle.SolvikUnit;

/**
 * The runtime representation of the mutable built-in {@code List<T>}. The erased runtime cannot
 * enforce the invariant element type, so the compiler checks it statically. Indices are zero-based;
 * an out-of-range index raises a Solvik bounds error anchored at the call node.
 *
 * <p>Two storages back the same member table. An integral list ({@code List<Integer>}, which
 * lowering selects from the resolved element type) stores elements in a primitive {@code int[]} and
 * avoids per-element boxing, as docs/LANGUAGE_SPEC.md section 10 requires; every other element type
 * stores erased {@code Object} values in an {@code ArrayList}. The choice is fixed at construction
 * from the invariant type argument, so no runtime type test happens per access and an element is
 * boxed only when it leaves the list as an erased value.
 */
public final class SolvikList extends SolvikBuiltinCollection {

    /** Element storage for an erased list. */
    private final ArrayList<Object> elements;

    /** Element storage for an integral list, or {@code null} for an erased one. */
    private int[] integralElements;

    private int integralSize;

    /**
     * Creates a list from erased initial elements, which may be empty.
     *
     * @param integral whether the resolved element type is {@code Integer}, selecting primitive
     *                storage; a source {@code List(elements...)} construction passes the type
     *                argument lowering already resolved
     */
    public SolvikList(Object[] initialElements, boolean integral) {
        super("List");
        if (integral) {
            // The array is sized for every initial element up front, so no growth is needed here.
            int[] storage = new int[Math.max(8, initialElements.length)];
            for (Object element : initialElements) {
                storage[integralSize] = elementValue(element, null);
                integralSize = integralSize + 1;
            }
            this.integralElements = storage;
            this.elements = null;
        } else {
            this.integralElements = null;
            this.elements = new ArrayList<>();
            for (Object element : initialElements) {
                this.elements.add(element);
            }
        }
    }

    @Override
    public int size() {
        return integralElements == null ? elements.size() : integralSize;
    }

    @Override
    public Object invoke(String memberName, Object[] arguments, Node location) {
        switch (memberName) {
            case "add":
                if (arguments.length != 1) {
                    throw SolvikException.arithmetic("add expects one argument", location);
                }
                if (integralElements == null) {
                    elements.add(arguments[0]);
                } else {
                    addIntegral(arguments[0], location);
                }
                return SolvikUnit.INSTANCE;
            case "get":
                if (arguments.length != 1) {
                    throw SolvikException.arithmetic("get expects one argument", location);
                }
                int getIndex = intValue(arguments[0], location);
                if (getIndex < 0 || getIndex >= size()) {
                    throw boundsFailure(getIndex, location);
                }
                return integralElements == null ? elements.get(getIndex) : Integer.valueOf(integralElements[getIndex]);
            case "set":
                if (arguments.length != 2) {
                    throw SolvikException.arithmetic("set expects two arguments", location);
                }
                int setIndex = intValue(arguments[0], location);
                if (setIndex < 0 || setIndex >= size()) {
                    throw boundsFailure(setIndex, location);
                }
                if (integralElements == null) {
                    elements.set(setIndex, arguments[1]);
                } else {
                    integralElements[setIndex] = elementValue(arguments[1], location);
                }
                return SolvikUnit.INSTANCE;
            case "removeAt":
                if (arguments.length != 1) {
                    throw SolvikException.arithmetic("removeAt expects one argument", location);
                }
                int removeIndex = intValue(arguments[0], location);
                if (removeIndex < 0 || removeIndex >= size()) {
                    throw boundsFailure(removeIndex, location);
                }
                if (integralElements == null) {
                    return elements.remove(removeIndex);
                }
                int removed = integralElements[removeIndex];
                System.arraycopy(integralElements, removeIndex + 1, integralElements, removeIndex, integralSize - removeIndex - 1);
                integralSize = integralSize - 1;
                return Integer.valueOf(removed);
            case "clear":
                if (integralElements == null) {
                    elements.clear();
                } else {
                    integralSize = 0;
                }
                return SolvikUnit.INSTANCE;
            case "size":
                return Integer.valueOf(size());
            case "isEmpty":
                return Boolean.valueOf(size() == 0);
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
        return SolvikException.boundsError("index " + index + " is out of range for list of size " + size(), location);
    }

    /** Appends one element to integral storage, growing it as needed. */
    private void addIntegral(Object element, Node location) {
        int value = elementValue(element, location);
        if (integralSize == integralElements.length) {
            integralElements = grow(integralElements, integralSize);
        }
        integralElements[integralSize] = value;
        integralSize = integralSize + 1;
    }

    /** Returns a storage array of at least twice {@code used} elements holding the first {@code used}. */
    private static int[] grow(int[] current, int used) {
        int[] grown = new int[Math.max(8, current.length * 2)];
        System.arraycopy(current, 0, grown, 0, used);
        return grown;
    }

    /** Reads the erased element of an integral list, which static analysis guarantees an Integer. */
    private static int elementValue(Object element, Node location) {
        if (element instanceof Integer i) {
            return i.intValue();
        }
        throw SolvikException.arithmetic("expected Integer element", location);
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
