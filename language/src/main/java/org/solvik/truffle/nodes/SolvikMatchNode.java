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
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikException;

/**
 * A lowered {@code match} expression (docs/LANGUAGE_SPEC.md section 12). The scrutinee is evaluated
 * once and the branches are tried in source order; the first matching clause evaluates its result.
 * Static analysis has already proven the match exhaustive for a known closed variant set (or
 * required a wildcard), so the fall-through path is unreachable for a well-typed program; it raises
 * a runtime type error rather than returning a silent default.
 */
@NodeInfo(shortName = "match", description = "A Solvik exhaustive match expression")
public final class SolvikMatchNode extends SolvikExpressionNode {

    @Child private SolvikExpressionNode scrutinee;
    @Children private final SolvikMatchClauseNode[] clauses;

    public SolvikMatchNode(SolvikExpressionNode scrutinee, SolvikMatchClauseNode[] clauses) {
        this.scrutinee = scrutinee;
        this.clauses = clauses;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object value = scrutinee.executeGeneric(frame);
        for (SolvikMatchClauseNode clause : clauses) {
            if (clause.matches(frame, value)) {
                return clause.execute(frame);
            }
        }
        throw noMatch(value);
    }

    @TruffleBoundary
    private SolvikException noMatch(Object value) {
        return SolvikException.typeError("no pattern matched the value in an exhaustive match", this);
    }
}
