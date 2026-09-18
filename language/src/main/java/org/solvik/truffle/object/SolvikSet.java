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
import com.oracle.truffle.api.nodes.Node;
import org.solvik.truffle.SolvikException;
import org.solvik.truffle.SolvikUnit;
import org.solvik.truffle.SolvikValues;

/**
 * The runtime representation of the mutable built-in {@code Set<T>}. Elements are erased to
 * {@code Object} and stored in an {@code ArrayList}; uniqueness, {@code contains}, and {@code remove}
 * search with {@link SolvikValues#equal} so scalars and enums compare by value while ordinary objects
 * compare by identity. Membership changes only when an element is newly added.
 */
public final class SolvikSet extends SolvikBuiltinCollection {

    private final ArrayList<Object> elements = new ArrayList<>();

    public SolvikSet() {
        super("Set");
    }

    /**
     * Pre-populates a set from erased elements, keeping the first occurrence of each equal element
     * so the uniqueness invariant that {@code add} maintains holds from construction.
     */
    public SolvikSet(Object[] initialElements) {
        this();
        for (Object element : initialElements) {
            if (!contains(element)) {
                elements.add(element);
            }
        }
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
                Object addValue = arguments[0];
                boolean addChanged = !contains(addValue);
                if (addChanged) {
                    elements.add(addValue);
                }
                return addChanged;
            case "contains":
                if (arguments.length != 1) {
                    throw SolvikException.arithmetic("contains expects one argument", location);
                }
                return contains(arguments[0]);
            case "remove":
                if (arguments.length != 1) {
                    throw SolvikException.arithmetic("remove expects one argument", location);
                }
                return remove(arguments[0]);
            case "isEmpty":
                return elements.isEmpty();
            case "size":
                return elements.size();
            case "clear":
                elements.clear();
                return SolvikUnit.INSTANCE;
            default:
                throw SolvikException.unknownCollectionMember(memberName, location);
        }
    }

    private boolean contains(Object value) {
        for (Object element : elements) {
            if (SolvikValues.equal(element, value)) {
                return true;
            }
        }
        return false;
    }

    private boolean remove(Object value) {
        for (int i = 0; i < elements.size(); i++) {
            if (SolvikValues.equal(elements.get(i), value)) {
                elements.remove(i);
                return true;
            }
        }
        return false;
    }
}
