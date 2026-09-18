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
 * The runtime representation of the mutable built-in {@code Map<K, V>}. Keys and values are erased to
 * {@code Object} in two parallel lists; lookup, replacement, and removal search with
 * {@link SolvikValues#equal} so they follow Solvik {@code ==} rather than Java {@code Map} key
 * semantics. A {@code put} replacing an equal key keeps {@code size} unchanged, a {@code get} for a
 * missing key raises a Solvik collection error, and a present {@code null} value is distinguishable
 * from an absent key through {@code containsKey}.
 */
public final class SolvikMap extends SolvikBuiltinCollection {

    private final ArrayList<Object> keys = new ArrayList<>();
    private final ArrayList<Object> values = new ArrayList<>();

    public SolvikMap() {
        super("Map");
    }

    /**
     * Pre-populates a map from parallel erased key and value arrays. A repeated key keeps its first
     * position but takes the later value, matching the replacement behavior of {@code put}.
     */
    public SolvikMap(Object[] keys, Object[] values) {
        this();
        for (int i = 0; i < keys.length; i++) {
            int index = indexOf(keys[i]);
            if (index >= 0) {
                this.values.set(index, values[i]);
            } else {
                this.keys.add(keys[i]);
                this.values.add(values[i]);
            }
        }
    }

    @Override
    public int size() {
        return keys.size();
    }

    @Override
    public Object invoke(String memberName, Object[] arguments, Node location) {
        switch (memberName) {
            case "put":
                if (arguments.length != 2) {
                    throw SolvikException.arithmetic("put expects two arguments", location);
                }
                int putIndex = indexOf(arguments[0]);
                if (putIndex >= 0) {
                    values.set(putIndex, arguments[1]);
                } else {
                    keys.add(arguments[0]);
                    values.add(arguments[1]);
                }
                return SolvikUnit.INSTANCE;
            case "get":
                if (arguments.length != 1) {
                    throw SolvikException.arithmetic("get expects one argument", location);
                }
                int getIndex = indexOf(arguments[0]);
                if (getIndex < 0) {
                    throw SolvikException.collectionError("no value for missing key", location);
                }
                return values.get(getIndex);
            case "containsKey":
                if (arguments.length != 1) {
                    throw SolvikException.arithmetic("containsKey expects one argument", location);
                }
                return indexOf(arguments[0]) >= 0;
            case "remove":
                if (arguments.length != 1) {
                    throw SolvikException.arithmetic("remove expects one argument", location);
                }
                int removeIndex = indexOf(arguments[0]);
                if (removeIndex < 0) {
                    return false;
                }
                keys.remove(removeIndex);
                values.remove(removeIndex);
                return true;
            case "isEmpty":
                return keys.isEmpty();
            case "size":
                return keys.size();
            case "clear":
                keys.clear();
                values.clear();
                return SolvikUnit.INSTANCE;
            default:
                throw SolvikException.unknownCollectionMember(memberName, location);
        }
    }

    private int indexOf(Object key) {
        for (int i = 0; i < keys.size(); i++) {
            if (SolvikValues.equal(keys.get(i), key)) {
                return i;
            }
        }
        return -1;
    }
}
