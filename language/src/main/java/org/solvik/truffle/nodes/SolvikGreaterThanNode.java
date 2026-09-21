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

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;

/** Solvik comparison on {@code Integer}, producing a primitive {@code boolean}. */
@NodeChild("leftNode")
@NodeChild("rightNode")
@NodeInfo(shortName = ">", description = "Solvik integer ordering comparison")
public abstract class SolvikGreaterThanNode extends SolvikExpressionNode {

    @Specialization
    protected boolean doInt(int left, int right) {
        return left > right;
    }

    @Fallback
    protected boolean doOther(Object left, Object right) {
        throw new IllegalStateException("invalid operands reached lowered '>'");
    }
}
