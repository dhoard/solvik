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

/**
 * The runtime representation of the mutable built-in {@code Stack<T>}: last-in, first-out storage
 * over an {@code ArrayList}. {@code peek} reads the top without mutating; {@code pop} removes and
 * returns it. An empty {@code peek} or {@code pop} raises a Solvik collection error.
 */
public final class SolvikStack extends SolvikBuiltinCollection {

    private final ArrayList<Object> elements = new ArrayList<>();

    public SolvikStack() {
        super("Stack");
    }

    public SolvikStack(Object[] initialElements) {
        this();
        for (Object element : initialElements) {
            this.elements.add(element);
        }
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public Object invoke(String memberName, Object[] arguments, Node location) {
        switch (memberName) {
            case "push":
                if (arguments.length != 1) {
                    throw SolvikException.arithmetic("push expects one argument", location);
                }
                elements.add(arguments[0]);
                return SolvikUnit.INSTANCE;
            case "pop":
                if (arguments.length != 0) {
                    throw SolvikException.arithmetic("pop expects no arguments", location);
                }
                if (elements.isEmpty()) {
                    throw SolvikException.collectionError("pop on empty stack", location);
                }
                return elements.remove(elements.size() - 1);
            case "peek":
                if (arguments.length != 0) {
                    throw SolvikException.arithmetic("peek expects no arguments", location);
                }
                if (elements.isEmpty()) {
                    throw SolvikException.collectionError("peek on empty stack", location);
                }
                return elements.get(elements.size() - 1);
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
}
