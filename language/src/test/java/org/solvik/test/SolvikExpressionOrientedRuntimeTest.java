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
package org.solvik.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.solvik.test.SolvikTestSupport.expectThrows;

import org.junit.jupiter.api.Test;
import org.solvik.truffle.nodes.SolvikBlockExprNode;
import org.solvik.truffle.nodes.SolvikBoolLiteralNode;
import org.solvik.truffle.nodes.SolvikIfExprNode;
import org.solvik.truffle.nodes.SolvikIntegerLiteralNode;
import org.solvik.truffle.nodes.SolvikStatementNode;

/**
 * Direct runtime-node tests for the value-producing expression nodes. They pin both the normal
 * value path and the defensive guards that a well-typed program can never reach, so the guards stay
 * honest and are never replaced by a fabricated default at runtime (docs/LANGUAGE_SPEC.md
 * section 21).
 */
public final class SolvikExpressionOrientedRuntimeTest {

    @Test
    public void blockExprReturnsItsTailValue() {
        SolvikBlockExprNode node = new SolvikBlockExprNode(new SolvikStatementNode[0], new SolvikIntegerLiteralNode(42));
        assertThat(node.executeGeneric(null)).isEqualTo(42);
    }

    @Test
    public void blockExprWithoutATailIsAnInternalError() {
        SolvikBlockExprNode node = new SolvikBlockExprNode(new SolvikStatementNode[0], null);
        expectThrows(IllegalStateException.class, () -> node.executeGeneric(null));
    }

    @Test
    public void ifExprSelectsTheThenValue() {
        SolvikIfExprNode node = new SolvikIfExprNode(new SolvikBoolLiteralNode(true), new SolvikIntegerLiteralNode(7), new SolvikIntegerLiteralNode(9));
        assertThat(node.executeGeneric(null)).isEqualTo(7);
    }

    @Test
    public void ifExprSelectsTheElseValue() {
        SolvikIfExprNode node = new SolvikIfExprNode(new SolvikBoolLiteralNode(false), new SolvikIntegerLiteralNode(7), new SolvikIntegerLiteralNode(9));
        assertThat(node.executeGeneric(null)).isEqualTo(9);
    }

    @Test
    public void ifExprWithoutAnElseIsAnInternalErrorOnTheFalsePath() {
        SolvikIfExprNode node = new SolvikIfExprNode(new SolvikBoolLiteralNode(false), new SolvikIntegerLiteralNode(7), null);
        expectThrows(IllegalStateException.class, () -> node.executeGeneric(null));
    }
}
