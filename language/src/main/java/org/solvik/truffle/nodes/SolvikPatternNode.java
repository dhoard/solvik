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
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * Base class of the runtime pattern matchers used by a lowered {@code match}
 * (docs/LANGUAGE_SPEC.md section 12). A pattern node tests one already-evaluated value and, when it
 * matches, writes the names it binds into frame slots so the selected branch's result expression can
 * read them. Patterns are not value-producing expressions, so this node extends {@link Node} rather
 * than {@link SolvikExpressionNode}.
 */
@NodeInfo(description = "A Solvik match pattern matcher")
public abstract class SolvikPatternNode extends Node {

    /** Whether {@code value} matches this pattern, binding names into {@code frame} when it does. */
    public abstract boolean matches(VirtualFrame frame, Object value);
}
