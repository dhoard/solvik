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
import com.oracle.truffle.api.nodes.Node.Children;
import com.oracle.truffle.api.nodes.NodeInfo;

/**
 * Solvik three-clause {@code for} loop. The update clause runs after every normal iteration and
 * after {@code continue}, but not after {@code break}; an omitted condition is {@code true}
 * (docs/LANGUAGE_SPEC.md section 17).
 */
@NodeInfo(shortName = "for", description = "A Solvik for loop")
public final class SolvikForNode extends SolvikStatementNode {

    @Children private final SolvikStatementNode[] initializer;
    @Child private SolvikExpressionNode condition;
    @Child private SolvikStatementNode update;
    @Child private SolvikStatementNode body;

    public SolvikForNode(SolvikStatementNode[] initializer, SolvikExpressionNode condition, SolvikStatementNode update, SolvikStatementNode body) {
        this.initializer = initializer;
        this.condition = condition;
        this.update = update;
        this.body = body;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        for (SolvikStatementNode statement : initializer) {
            statement.executeVoid(frame);
        }
        while (condition == null || condition.executeBoolean(frame)) {
            try {
                body.executeVoid(frame);
            } catch (SolvikContinueException e) {
                // Fall through to the update clause.
            } catch (SolvikBreakException e) {
                break;
            }
            if (update != null) {
                update.executeVoid(frame);
            }
        }
    }
}
