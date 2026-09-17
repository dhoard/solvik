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
import org.solvik.truffle.object.SolvikEnumValue;
import org.solvik.truffle.object.SolvikEnumVariant;

/**
 * An enum variant pattern (docs/LANGUAGE_SPEC.md section 12). The variant is fixed when the node is
 * created from the compiler's closed-variant metadata, so the test is a direct variant-identity
 * comparison followed by the nested argument patterns. Each argument pattern is matched against the
 * corresponding positional value of the enum value.
 */
@NodeInfo(shortName = "variant", description = "A Solvik enum variant pattern")
public final class SolvikEnumPatternNode extends SolvikPatternNode {

    private final SolvikEnumVariant variant;
    @Children private final SolvikPatternNode[] arguments;

    public SolvikEnumPatternNode(SolvikEnumVariant variant, SolvikPatternNode[] arguments) {
        this.variant = variant;
        this.arguments = arguments;
    }

    @Override
    public boolean matches(VirtualFrame frame, Object value) {
        if (!(value instanceof SolvikEnumValue enumValue) || enumValue.variant() != variant) {
            return false;
        }
        for (int i = 0; i < arguments.length; i++) {
            if (!arguments[i].matches(frame, enumValue.value(i))) {
                return false;
            }
        }
        return true;
    }
}
