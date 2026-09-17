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
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.object.SolvikEnumVariant;
import org.solvik.truffle.object.SolvikEnumValue;

/**
 * Constructs a Solvik enum value from a statically resolved variant (docs/LANGUAGE_SPEC.md
 * section 12). The variant's identity is fixed when the node is created, so no runtime lookup is
 * needed; the node evaluates its positional values and yields an immutable enum value.
 */
@NodeInfo(shortName = "new-enum", description = "Construct a Solvik enum variant value")
public final class SolvikEnumConstructNode extends SolvikExpressionNode {

    private final SolvikEnumVariant variant;
    @Children private final SolvikExpressionNode[] values;

    public SolvikEnumConstructNode(SolvikEnumVariant variant, SolvikExpressionNode[] values) {
        this.variant = variant;
        this.values = values;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object[] evaluated = new Object[values.length];
        for (int i = 0; i < evaluated.length; i++) {
            evaluated[i] = values[i].executeGeneric(frame);
        }
        return new SolvikEnumValue(variant, evaluated);
    }
}
