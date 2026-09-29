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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.object.SolvikFunctionValue;

/**
 * Yields the canonical function value of one declared function (docs/LANGUAGE_SPEC.md section 6,
 * "Named functions as values").
 *
 * <p>Like a {@link SolvikNullLiteralNode} this is a constant, and for the same reason it is not a
 * {@code DAGConstantNode}: a function value is an executable object with a call target, not a
 * primitive the engine can fold. The value it returns is the one the lowering holds for that
 * declaration, created once, so every read of one function's name in one program yields one identity
 * — module qualification included, because a qualified read resolves to the same declaration and
 * therefore to this same node's value.
 *
 * <p>Lowering uses {@link #value()} to prove to a {@link SolvikIndirectCallNode} that the callee
 * cannot change, which is what lets an indirect call to a named function specialize to a direct call.
 */
@NodeInfo(shortName = "func", description = "A reference to a declared function")
public final class SolvikFunctionValueNode extends SolvikExpressionNode {

    @CompilationFinal private final SolvikFunctionValue value;

    public SolvikFunctionValueNode(SolvikFunctionValue value) {
        this.value = value;
    }

    /** The value this node always yields. */
    public SolvikFunctionValue value() {
        return value;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return value;
    }
}
