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

/** The {@code null} literal (docs/LANGUAGE_SPEC.md section 5); its value is the runtime null. */
@NodeInfo(shortName = "null", description = "The Solvik null literal")
public final class SolvikNullLiteralNode extends SolvikExpressionNode {

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return null;
    }
}
