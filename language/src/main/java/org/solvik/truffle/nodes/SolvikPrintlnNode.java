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
import org.solvik.truffle.SolvikContext;
import org.solvik.truffle.SolvikUnit;

/** The predeclared Solvik {@code println(value: Any): Unit} function (docs/LANGUAGE_SPEC.md section 6). */
@NodeInfo(shortName = "println", description = "Writes a Solvik value followed by a line separator")
public final class SolvikPrintlnNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode argument;

    public SolvikPrintlnNode(SolvikExpressionNode argument) {
        this.argument = argument;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        SolvikContext.get(this).println(argument.executeGeneric(frame));
        return SolvikUnit.INSTANCE;
    }
}
