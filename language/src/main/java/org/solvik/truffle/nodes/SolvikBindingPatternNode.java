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
import org.solvik.truffle.object.SolvikClass;
import org.solvik.truffle.object.SolvikRuntimeTypes;
import org.solvik.type.Type;

/**
 * A binding pattern (docs/LANGUAGE_SPEC.md section 12). A {@code name: Type} pattern first tests the
 * runtime type and then binds the value; a bare variant binding has no target type and binds
 * unconditionally. The binding's frame slot is fixed by lowering and always stores its value as an
 * object, because the bound value comes from a destructured enum value or a subtype test.
 */
@NodeInfo(shortName = "bind", description = "A Solvik binding pattern")
public final class SolvikBindingPatternNode extends SolvikPatternNode {

    private final int slot;
    /** The tested subtype, or {@code null} for a bare binding that matches every value. */
    private final Type target;
    private final SolvikClass targetClass;

    public SolvikBindingPatternNode(int slot, Type target, SolvikClass targetClass) {
        this.slot = slot;
        this.target = target;
        this.targetClass = targetClass;
    }

    @Override
    public boolean matches(VirtualFrame frame, Object value) {
        if (target != null && !SolvikRuntimeTypes.isInstance(value, target, targetClass)) {
            return false;
        }
        frame.setObject(slot, value);
        return true;
    }
}
