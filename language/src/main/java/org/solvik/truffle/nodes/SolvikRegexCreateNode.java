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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node.Child;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.regex.RegexPattern;
import org.solvik.regex.RegexSyntax;
import org.solvik.truffle.SolvikException;
import org.solvik.truffle.object.SolvikRegex;

/**
 * A {@code Regex(pattern)} construction whose pattern is only known at run time
 * (docs/LANGUAGE_SPEC.md section 14). The pattern is validated against the portable dialect and
 * compiled when it is first seen; an invalid or unsupported pattern raises a Solvik runtime regex
 * error. The last compiled pattern is cached so a construction repeated with the same text does not
 * recompile (a source constant never reaches this node: it uses
 * {@link SolvikRegexLiteralNode}).
 */
@NodeInfo(shortName = "Regex", description = "A dynamically constructed Solvik Regex value")
public final class SolvikRegexCreateNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode pattern;
    private RegexPattern cached;

    public SolvikRegexCreateNode(SolvikExpressionNode pattern) {
        this.pattern = pattern;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        String source = (String) pattern.executeGeneric(frame);
        RegexPattern compiled = cached;
        if (compiled == null || !compiled.source().equals(source)) {
            compiled = compile(source);
            cached = compiled;
        }
        return new SolvikRegex(compiled.source(), compiled.compiled());
    }

    @TruffleBoundary
    private RegexPattern compile(String source) {
        try {
            return RegexSyntax.compile(source);
        } catch (RegexSyntax.InvalidPatternException e) {
            throw SolvikException.regexError(e.getMessage(), this);
        }
    }
}
