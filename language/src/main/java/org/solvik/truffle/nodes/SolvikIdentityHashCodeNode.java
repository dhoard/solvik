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
import org.solvik.truffle.SolvikHash;

/**
 * The root identity default of {@code Any.hashCode()} reached through {@code super.hashCode()} when
 * no class in the hierarchy overrides the member (docs/LANGUAGE_SPEC.md section 3).
 *
 * <p>{@code super.hashCode()} with an inherited override lowers to an ordinary receiver call, so this
 * node only appears when the lookup found no source override and there is no runtime function to
 * call. It reports the reference identity hash of its operand, which is the same fallback
 * {@link SolvikHash} uses for a user object with no override, keeping the hash consistent with the
 * {@code super.equals} identity default. Re-dispatching instead of using this default would call the
 * current class's own override again and recurse forever.
 */
@NodeInfo(shortName = "superHashCode", description = "The root identity-default hash of a receiver")
public final class SolvikIdentityHashCodeNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode operand;

    public SolvikIdentityHashCodeNode(SolvikExpressionNode operand) {
        this.operand = operand;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return SolvikHash.identityHash(operand.executeGeneric(frame));
    }
}
