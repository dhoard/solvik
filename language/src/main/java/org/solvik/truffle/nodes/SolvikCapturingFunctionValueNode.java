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
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.RootCallTarget;
import org.solvik.truffle.object.SolvikFunctionValue;

/**
 * Produces the value of an anonymous function expression whose capture list is non-empty
 * (docs/LANGUAGE_SPEC.md section 6, "Explicit immutable closure capture"): "Each listed binding's value is
 * captured when evaluation reaches the anonymous-function expression."
 *
 * <p>So evaluation reads each captured binding here, in capture-list order, and stores those values in a
 * freshly allocated {@link SolvikFunctionValue}. Reading them at this moment is what makes a capture a
 * value rather than a reference to a storage location: reassigning an enclosing binding afterwards cannot
 * change what an existing closure holds, and a closure stays valid after the function that created it
 * returns because nothing it holds depends on that frame still existing.
 *
 * <h2>Why an expression, not a constant</h2>
 *
 * {@link SolvikAnonymousFunctionValueNode} is the capture-free sibling and allocates from one shared
 * target with no state. This node differs only in the state it threads in, and it keeps the same
 * allocation rule: a new value on every evaluation, so two evaluations of one expression are never
 * {@code ===} even when both captured the same values. {@link #captures} is {@code @Children} and not a
 * {@code final} field because a captured value is read from the enclosing frame and so must be evaluated
 * with this expression's other operands; a {@code final} array of nodes could not be.
 *
 * <p>The {@link #target} stays a {@code final} field rather than a {@code @Child} for the reason its
 * sibling gives: every value this expression ever creates invokes the same code, so the target belongs to
 * no one value, and sharing it is what keeps a fresh closure cheap.
 */
@NodeInfo(shortName = "func", description = "An anonymous function expression with a capture list")
public final class SolvikCapturingFunctionValueNode extends SolvikExpressionNode {

    private final RootCallTarget target;
    private final String debugName;
    @Children private final SolvikExpressionNode[] captures;

    public SolvikCapturingFunctionValueNode(RootCallTarget target, SolvikExpressionNode[] captures, String debugName) {
        this.target = target;
        this.captures = captures.clone();
        this.debugName = debugName;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object[] captured = new Object[captures.length];
        for (int i = 0; i < captures.length; i++) {
            captured[i] = captures[i].executeGeneric(frame);
        }
        return SolvikFunctionValue.capturing(target, captured, debugName);
    }
}
