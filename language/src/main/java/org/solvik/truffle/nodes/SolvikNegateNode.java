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

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.NodeInfo;
import org.solvik.truffle.SolvikException;

/** Solvik unary {@code -} on {@code Integer}, checked for 32-bit overflow. */
@NodeChild("valueNode")
@NodeInfo(shortName = "-", description = "Solvik integer negation")
public abstract class SolvikNegateNode extends SolvikExpressionNode {

    @Specialization
    protected int doInt(int value) {
        try {
            return Math.negateExact(value);
        } catch (ArithmeticException e) {
            throw SolvikException.arithmetic("integer overflow in unary '-'", this);
        }
    }

    @Fallback
    protected int doOther(Object value) {
        throw new IllegalStateException("invalid operand reached lowered unary '-'");
    }
}
