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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.type.BuiltinCollectionType;
import org.solvik.truffle.object.SolvikList;
import org.solvik.truffle.object.SolvikMap;
import org.solvik.truffle.object.SolvikSet;
import org.solvik.truffle.object.SolvikStack;

/**
 * Allocates an empty built-in collection for a source {@code List<T>()}, {@code Set<T>()},
 * {@code Map<K, V>()}, or {@code Stack<T>()} construction. The erased runtime cannot recover the
 * type arguments from the erased receiver, so lowering carries the resolved descriptor here and the
 * node selects the matching runtime implementation. Collections have zero-argument constructors, so
 * no value arguments are evaluated.
 */
@NodeInfo(shortName = "collection", description = "Allocate an empty or pre-populated Solvik built-in collection")
public final class SolvikCollectionConstructNode extends SolvikExpressionNode {

    private final BuiltinCollectionType type;
    private final SolvikExpressionNode[] valueArguments;

    public SolvikCollectionConstructNode(BuiltinCollectionType type, SolvikExpressionNode[] valueArguments) {
        this.type = type;
        this.valueArguments = valueArguments;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object[] executed = new Object[valueArguments.length];
        for (int i = 0; i < valueArguments.length; i++) {
            executed[i] = valueArguments[i].executeGeneric(frame);
        }
        String name = type.name();
        return switch (name) {
            case "List" -> new SolvikList(executed);
            case "Set" -> new SolvikSet(executed);
            case "Stack" -> new SolvikStack(executed);
            case "Map" -> {
                Object[] keys = new Object[executed.length / 2];
                Object[] values = new Object[executed.length / 2];
                for (int i = 0; i < keys.length; i++) {
                    keys[i] = executed[2 * i];
                    values[i] = executed[2 * i + 1];
                }
                yield new SolvikMap(keys, values);
            }
            default -> throw new IllegalStateException("unknown collection type '" + name + "'");
        };
    }
}
