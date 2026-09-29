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
import com.oracle.truffle.api.RootCallTarget;
import org.solvik.truffle.object.SolvikFunctionValue;

/**
 * Produces the value of an anonymous function expression (docs/LANGUAGE_SPEC.md section 6, "Anonymous
 * functions"): "An anonymous function creates a new function value every time evaluation reaches the
 * expression, and two evaluations are distinct even when the expression captures no values."
 *
 * <p>This is the opposite of {@link SolvikFunctionValueNode}, which is a constant holding one canonical
 * value for a named function. A reference to a name is not an allocation; this expression is. So each
 * {@link #executeGeneric} allocates, and two evaluations of one occurrence of the expression are never
 * {@code ===} to each other. Re-reading a binding that already holds one of these values is a different
 * expression and yields the same identity it stored, which is why re-reading a local preserves identity
 * without any caching here — caching would be wrong, because it would make one expression's value one
 * value and contradict the sentence above.
 *
 * <p>The {@link #target} is a {@code final} field and not a {@code @Child}: it is shared by every value
 * this expression ever creates, so it cannot be owned by any one of them, and a call target is a
 * runtime object rather than a node in this tree. Sharing the target is also what keeps fresh identity
 * cheap — allocation is one small value object, and the body compiles and inlines once regardless of
 * how many values reference it.
 */
@NodeInfo(shortName = "func", description = "An anonymous function expression")
public final class SolvikAnonymousFunctionValueNode extends SolvikExpressionNode {

    private final RootCallTarget target;
    private final String debugName;

    public SolvikAnonymousFunctionValueNode(RootCallTarget target, String debugName) {
        this.target = target;
        this.debugName = debugName;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return SolvikFunctionValue.forTarget(target, debugName);
    }
}
