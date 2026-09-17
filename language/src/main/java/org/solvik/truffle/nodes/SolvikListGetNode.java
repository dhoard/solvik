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
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.object.SolvikList;

/**
 * Reads an element of a built-in {@code List<T>} by index (docs/LANGUAGE_SPEC.md section 11),
 * raising a Solvik runtime bounds error when the index is out of range. The element type was
 * checked statically and is erased at run time.
 */
@NodeInfo(shortName = ".get", description = "Read a Solvik List element")
public final class SolvikListGetNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode receiver;
    @Child private SolvikExpressionNode index;

    public SolvikListGetNode(SolvikExpressionNode receiver, SolvikExpressionNode index) {
        this.receiver = receiver;
        this.index = index;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object target = receiver.executeGeneric(frame);
        int position = index.executeInt(frame);
        return ((SolvikList) target).get(position, this);
    }
}
