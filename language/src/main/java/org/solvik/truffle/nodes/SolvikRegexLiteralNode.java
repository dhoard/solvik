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
import org.solvik.regex.RegexPattern;
import org.solvik.truffle.object.SolvikRegex;

/**
 * A {@code Regex(pattern)} construction whose pattern is a source constant
 * (docs/LANGUAGE_SPEC.md section 14). Static analysis compiled the pattern exactly once and lowering
 * built this node once, so every execution reuses the same compiled pattern and the same runtime
 * value rather than recompiling inside a loop.
 */
@NodeInfo(shortName = "Regex", description = "A constant Solvik Regex value")
public final class SolvikRegexLiteralNode extends SolvikExpressionNode {

    private final SolvikRegex value;

    public SolvikRegexLiteralNode(RegexPattern pattern) {
        this.value = new SolvikRegex(pattern.source(), pattern.compiled());
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        return value;
    }
}
