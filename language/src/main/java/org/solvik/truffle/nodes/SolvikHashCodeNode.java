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
import org.solvik.truffle.SolvikValues;

/**
 * The built-in {@code Any.hashCode(): Integer} of any Solvik value
 * (docs/LANGUAGE_SPEC.md section 3).
 *
 * <p>A user object dispatches its effective {@code hashCode} override and every other value uses the
 * fixed rule for its kind; both paths live in {@link SolvikHash}, which mirrors the structure of
 * {@link SolvikValues} and satisfies the invariant that values which are
 * {@linkplain SolvikValues#equal equal} always hash alike. A safe call ({@code receiver?.hashCode()})
 * on a null receiver yields {@code null} instead of the hash of {@code null}.
 */
@NodeInfo(shortName = "hashCode", description = "Computes the semantic hash of any Solvik value")
public final class SolvikHashCodeNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode operand;
    private final boolean safe;

    public SolvikHashCodeNode(SolvikExpressionNode operand, boolean safe) {
        this.operand = operand;
        this.safe = safe;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object value = operand.executeGeneric(frame);
        if (value == null) {
            return safe ? null : SolvikHash.hash(null);
        }
        return SolvikHash.hash(value);
    }
}
