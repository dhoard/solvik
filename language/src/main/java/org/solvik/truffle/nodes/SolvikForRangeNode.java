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
import org.solvik.ast.statement.RangeOperator;

/**
 * Solvik range {@code for}-in loop (docs/LANGUAGE_SPEC.md section 17). Both bounds are evaluated
 * once; the loop variable is written into its frame slot before each iteration. The iteration count
 * is computed in {@code long} so an inclusive range ending at {@code Int.MAX_VALUE} (or one starting
 * at {@code Int.MIN_VALUE}) cannot overflow. {@code continue} advances and {@code break} stops, the
 * same behavior as the three-clause {@code for}.
 */
@NodeInfo(shortName = "for-in", description = "A Solvik range for-in loop")
public final class SolvikForRangeNode extends SolvikStatementNode {

    private final RangeOperator operator;
    private final int loopSlot;
    @Child private SolvikExpressionNode start;
    @Child private SolvikExpressionNode end;
    @Child private SolvikStatementNode body;

    public SolvikForRangeNode(RangeOperator operator, SolvikExpressionNode start, SolvikExpressionNode end, int loopSlot, SolvikStatementNode body) {
        this.operator = operator;
        this.start = start;
        this.end = end;
        this.loopSlot = loopSlot;
        this.body = body;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        long from = start.executeInt(frame);
        long to = end.executeInt(frame);
        long count = switch (operator) {
            case INCLUSIVE -> to - from + 1;
            case EXCLUSIVE_ASCENDING -> to - from;
            case EXCLUSIVE_DESCENDING -> from - to;
        };
        if (count <= 0) {
            return;
        }
        boolean ascending = operator.isAscending();
        for (long step = 0; step < count; step++) {
            int value = (int) (ascending ? from + step : from - step);
            frame.setInt(loopSlot, value);
            try {
                body.executeVoid(frame);
            } catch (SolvikContinueException e) {
                continue;
            } catch (SolvikBreakException e) {
                break;
            }
        }
    }
}
