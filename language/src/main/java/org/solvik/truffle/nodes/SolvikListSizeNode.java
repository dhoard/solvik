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
 * Reads the {@code size} of a built-in {@code List<T>} (docs/LANGUAGE_SPEC.md section 11). The
 * built-in list has no declared property storage, so lowering emits this dedicated read instead of
 * a Truffle shape access.
 */
@NodeInfo(shortName = ".size", description = "Read the size of a Solvik List")
public final class SolvikListSizeNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode receiver;

    public SolvikListSizeNode(SolvikExpressionNode receiver) {
        this.receiver = receiver;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return ((SolvikList) receiver.executeGeneric(frame)).size();
    }

    @Override
    public int executeInt(VirtualFrame frame) {
        return ((SolvikList) receiver.executeGeneric(frame)).size();
    }
}
